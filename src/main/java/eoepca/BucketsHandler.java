package eoepca;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.S3Actions;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import eoepca.crd.BucketResource;
import eoepca.crd.BucketResourceDoneable;
import eoepca.crd.BucketResourceList;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.log4j.Log4j2;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.client.IOSClientBuilder;
import org.openstack4j.api.exceptions.ClientResponseException;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Credential;
import org.openstack4j.model.identity.v3.Project;
import org.openstack4j.model.identity.v3.User;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.CreateUpdateContainerOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static eoepca.Convert.forId;

@Component
@Log4j2
public class BucketsHandler implements ApplicationListener<ContextRefreshedEvent> {

	@Autowired
	private IOSClientBuilder.V3 osClientBuilder;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private SecretService secretService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NonNamespaceOperation<BucketResource, BucketResourceList, BucketResourceDoneable, Resource<BucketResource, BucketResourceDoneable>> bucketClient;

	@Autowired
	private KubernetesClient k8sClient;

	@Autowired
	@Qualifier("version")
	private String version;

	private Counter os4jRequests;

	private final Map<String, BucketResource> cache = new ConcurrentHashMap<>();

	@Value("${OS_USERNAME}")
	String osUsername;

	@Value("${OS_PASSWORD}")
	String osPassword;

	@Value("${OS_DOMAINNAME}")
	String osDomainname;

	@Value("${OS_MEMBERROLEID}")
	String osMemberRoleId;

	@Value("${OS_SERVICEPROJECTID}")
	String osServiceProjectId;

	@Value("${USER_EMAIL_PATTERN}")
	String userEmailPattern;

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		log.info("onApplicationEvent: " + Convert.toStr(event));
		Gauge.builder("buckets", () -> cache.size()).register(meterRegistry);
		os4jRequests = meterRegistry.counter("os4j.requests");
		new Thread(this::listThenWatch).start();
	}

	public void listThenWatch() {
		try {
			bucketClient
				.list()
				.getItems()
				.forEach(bucketResource -> {
						try {
							cache.put(bucketResource.getMetadata().getUid(), bucketResource);
							handleAddOrUpdate(bucketResource);
						} catch (Exception e) {
							log.error("catch up for {} failed: {}", bucketResource, e);
						}
					}
				);
			bucketClient.watch(new Watcher<BucketResource>() {
				@Override
				public void eventReceived(Action action, BucketResource bucketResource) {
					try {
						String uid = bucketResource.getMetadata().getUid();
						if (cache.containsKey(uid)) {
							int knownResourceVersion = Integer.parseInt(cache.get(uid).getMetadata().getResourceVersion());
							int receivedResourceVersion = Integer.parseInt(bucketResource.getMetadata().getResourceVersion());
							if (knownResourceVersion > receivedResourceVersion) {
								return;
							}
						}
						if (action == Action.ADDED || action == Action.MODIFIED) {
							cache.put(uid, bucketResource);
							handleAddOrUpdate(bucketResource);
						} else if (action == Action.DELETED) {
							log.info("bucketDeleted for {} not implemented yet", bucketResource);
							cache.remove(uid);
						} else {
							log.error("{} for {} unhandled", action, bucketResource);
						}
					} catch (Exception e) {
						log.error("{} for {} failed: {}", action, bucketResource, e);
					}
				}

				@Override
				public void onClose(KubernetesClientException e) {
					log.info("watcher closed", e);
					bucketClient.watch(this);
				}
			});
		} catch (Exception e) {
			log.error("listThenWatch failed", e);
		}
	}

	private void handleAddOrUpdate(BucketResource bucketResource) {
		Tracer.Throwing("handleAddOrUpdate", bucketResource, () -> {
			if (bucketResource.getSpec().getSecretName() == null
				|| bucketResource.getSpec().getSecretNamespace() == null
				|| bucketResource.getSpec().getBucketName() == null) {
				log.info("skipping invalid bucket {}", bucketResource.getSpec());
				return;
			}
			if (!secretService.getBucketSecret(bucketResource).isPresent()) {
				os4jRequests.increment();
				String name = forId(bucketResource.getSpec().getBucketName());
				OSClient.OSClientV3 osMaster = osClientBuilder
					.credentials(osUsername, osPassword, Identifier.byName(osDomainname))
					.scopeToDomain(Identifier.byName(osDomainname))
					.authenticate();
				String domainId = osMaster.getToken().getDomain().getId();
				Project project = null;
				try {
					project = osMaster.identity().projects()
						.create(Builders.project()
							.name("project-" + name)
							.domainId(domainId)
							.enabled(true)
							.build());
					log.info("created project {}", project);
				} catch (ClientResponseException ex) {
					if (ex.getStatusCode().getCode() != 409)
						throw ex;
					project = osMaster.identity().projects().getByName("project-" + name, domainId);
				}
				User user = null;
				String password = name + "+" + project.getId();
				try {
					user = osMaster.identity().users().create(Builders.user()
						.name("user-" + name)
						.password(password)
						.email(userEmailPattern.replace("<name>", name))
						.domainId(project.getDomainId())
						.defaultProjectId(project.getId())
						.build());
					log.info("created user {}", user);
				} catch (ClientResponseException ex) {
					if (ex.getStatusCode().getCode() != 409)
						throw ex;
					user = osMaster.identity().users().getByName("user-" + name, domainId);
				}
				try {
					osMaster.identity().roles().grantProjectUserRole(project.getId(), osMaster.getToken().getUser().getId(), osMemberRoleId);
				} catch (Exception ex) {
					// ignore
				}
				try {
					osMaster.identity().roles().grantProjectUserRole(project.getId(), user.getId(), osMemberRoleId);
				} catch (Exception ex) {
					// ignore
				}
				Credential credential = null;
				String access = name + "+" + UUID.randomUUID().toString().substring(0, 10);
				String secret = name + "+" + UUID.randomUUID().toString().substring(0, 10);
				String blob = objectMapper.writeValueAsString(new HashMap<String, String>() {{
					put("access", access);
					put("secret", secret);
				}});
				try {
					credential = osMaster.identity().credentials().create(Builders.credential()
						.id("credential-" + name)
						.blob(blob)
						.type("ec2")
						.projectId(project.getId())
						.userId(user.getId())
						.build());
					log.info("created credential {}", credential);
				} catch (ClientResponseException ex) {
					if (ex.getStatusCode().getCode() != 409)
						throw ex;
					credential = osMaster.identity().credentials().get("credential-" + name);
				}
				OSClient.OSClientV3 osUser = osClientBuilder
					.credentials("user-" + name, password, Identifier.byId(domainId))
					.scopeToProject(Identifier.byId(project.getId()), Identifier.byId(domainId))
					.authenticate();
				Map<String, String> metaData = new HashMap<>();
				metaData.put("createdBy", "bucket-operator-" + version);
				ActionResponse actionResponse = osUser.objectStorage().containers().create(bucketResource.getSpec().getBucketName(), CreateUpdateContainerOptions.create()
					.metadata(metaData)
				);
				log.info("container creation action for {} returned {}", bucketResource, actionResponse);
				try {
					AWSCredentials credentials = new BasicAWSCredentials(
						access,
						secret
					);
					ClientConfiguration clientConfiguration = new ClientConfiguration();
					clientConfiguration.setProtocol(Protocol.HTTPS);
					AmazonS3 s3client = AmazonS3ClientBuilder
						.standard()
						.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("cf2.cloudferro.com:8080", "RegionOne"))
						.withCredentials(new AWSStaticCredentialsProvider(credentials))
						.withPathStyleAccessEnabled(true)
						.withClientConfiguration(clientConfiguration)
						.build();
					String policyText = new Policy().withStatements(new Statement(Statement.Effect.Allow)
						.withPrincipals(new Principal("AWS", "arn:aws:iam::" + osServiceProjectId + ":root"))
						.withActions(S3Actions.ListObjects, S3Actions.PutObject, S3Actions.DeleteObject, S3Actions.GetObject)
						.withResources(new com.amazonaws.auth.policy.Resource(("arn:aws:s3:::" + bucketResource.getSpec().getBucketName() + "/*")))).toJson();
					log.info("bucket policy {}", policyText);
					s3client.setBucketPolicy(bucketResource.getSpec().getBucketName(), policyText);
				} catch (Exception ex) {
					log.error("setting bucket policy for {} failed", bucketResource.getSpec().getBucketName(), ex);
				}
				try {
					String jobName = "notify-" + bucketResource.getSpec().getBucketName() + UUID.randomUUID().toString();
					Job job = new JobBuilder()
						.withApiVersion("batch/v1")
						.withNewMetadata()
						.withName(jobName)
						.withLabels(Collections.singletonMap("created-by", "bucket-operator-" + version))
						.endMetadata()
						.withNewSpec()
						.withNewTemplate()
						.withNewSpec()
						.addNewContainer()
						.withName("notify")
						.withImage("python:3.9.2-alpine")
						.withArgs("echo", String.format("project=%s/%s user=%s/%s bucket=%s", project.getId(), project.getName(), user.getId(), user.getName(), bucketResource.getSpec().getBucketName()))
						.endContainer()
						.withRestartPolicy("Never")
						.endSpec()
						.endTemplate()
						.endSpec()
						.build();
					k8sClient.batch().jobs().inNamespace(bucketResource.getSpec().getSecretNamespace()).createOrReplace(job);
					PodList podList = k8sClient.pods().inNamespace(bucketResource.getSpec().getSecretNamespace()).withLabel("job-name", job.getMetadata().getName()).list();
					k8sClient.pods().inNamespace(bucketResource.getSpec().getSecretNamespace()).withName(podList.getItems().get(0).getMetadata().getName())
						.waitUntilCondition(pod -> pod.getStatus().getPhase().equals("Succeeded"), 1, TimeUnit.MINUTES);
					String joblog = k8sClient.batch().jobs().inNamespace(bucketResource.getSpec().getSecretNamespace()).withName(jobName).getLog();
					log.info("notify for {} succeeded: {}", bucketResource.getSpec().getBucketName(), joblog);
				} catch (Exception ex) {
					log.error("notify for {} failed", bucketResource.getSpec().getBucketName(), ex);
				}
				Optional<? extends SwiftContainer> container = osUser.objectStorage().containers().list(ContainerListOptions.create().path("/" + bucketResource.getSpec().getBucketName())).stream().findFirst();
				if (container.isPresent()) {
					log.info("container {} with metadata {} exists", container.get(), container.get().getMetadata());
					Map<String, String> data = objectMapper.readValue(blob, Map.class);
					data.put("bucketname", container.get().getName());
					data.put("projectid", project.getId());
					if (secretService.createBucketSecret(bucketResource, data)) {
						log.info("secret creation for container {} succeeded", bucketResource.getSpec().getBucketName());
					}
				} else {
					log.error("container {} not found", bucketResource.getSpec().getBucketName());
				}
			}
		});
	}
}
