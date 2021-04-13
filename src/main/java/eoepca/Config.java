package eoepca;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eoepca.crd.BucketResource;
import eoepca.crd.BucketResourceDoneable;
import eoepca.crd.BucketResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.openstack4j.api.client.IOSClientBuilder;
import org.openstack4j.openstack.OSFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@Log4j2
public class Config {

	@Value("${k8s.cluster}")
	private String cluster;

	@Value("${k8s.namespace}")
	String masterNamespace;

	@Bean
	public KubernetesClient kubernetesClient() {
		return Tracer.Throwing("starting k8s client", () -> {
			KubernetesClient kubernetesClient = new DefaultKubernetesClient(io.fabric8.kubernetes.client.Config.autoConfigure(cluster));
			return kubernetesClient;
		});
	}

	@Bean
	public NonNamespaceOperation<BucketResource, BucketResourceList, BucketResourceDoneable, Resource<BucketResource, BucketResourceDoneable>> bucketClient(KubernetesClient defaultClient) {
		return Tracer.Throwing("registering Bucket CRD", () -> {
			KubernetesDeserializer.registerCustomKind("epca.eo/v1alpha1", "Bucket", BucketResource.class);
			CustomResourceDefinition crd = defaultClient.customResourceDefinitions().createOrReplace(new CustomResourceDefinitionBuilder().
				withApiVersion("apiextensions.k8s.io/v1beta1").
				withNewMetadata().withName("buckets.epca.eo").endMetadata().
				withNewSpec().withGroup("epca.eo").withVersion("v1alpha1").withScope("Namespaced").
				withNewNames().withKind("Bucket").withShortNames("bucket").withPlural("buckets").endNames().endSpec().
				build());
			return defaultClient
				.customResources(crd, BucketResource.class, BucketResourceList.class, BucketResourceDoneable.class)
				.inNamespace(masterNamespace);
		});
	}

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		return mapper;
	}

	private static final int CONNECT_TIMEOUT = 30000;
	private static final int REQUEST_TIMEOUT = 30000;
	private static final int SOCKET_TIMEOUT = 60000;
	private static final int MAX_TOTAL_CONNECTIONS = 50;
	private static final int DEFAULT_KEEP_ALIVE_TIME_MILLIS = 20 * 1000;
	private static final int CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS = 30;

	@Bean
	public PoolingHttpClientConnectionManager poolingConnectionManager() {
		SSLContextBuilder builder = new SSLContextBuilder();
		try {
			builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		} catch (NoSuchAlgorithmException | KeyStoreException e) {
			log.error("Pooling Connection Manager Initialisation failure because of " + e.getMessage(), e);
		}
		SSLConnectionSocketFactory sslsf = null;
		try {
			sslsf = new SSLConnectionSocketFactory(builder.build());
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			log.error("Pooling Connection Manager Initialisation failure because of " + e.getMessage(), e);
		}
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
			.<ConnectionSocketFactory>create().register("https", sslsf)
			.register("http", new PlainConnectionSocketFactory())
			.build();
		PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		poolingConnectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
		return poolingConnectionManager;
	}

	@Bean
	public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
		return new ConnectionKeepAliveStrategy() {
			@Override
			public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
				HeaderElementIterator it = new BasicHeaderElementIterator
					(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
				while (it.hasNext()) {
					HeaderElement he = it.nextElement();
					String param = he.getName();
					String value = he.getValue();
					if (value != null && param.equalsIgnoreCase("timeout")) {
						return Long.parseLong(value) * 1000;
					}
				}
				return DEFAULT_KEEP_ALIVE_TIME_MILLIS;
			}
		};
	}

	@Bean
	public CloseableHttpClient httpClient() {
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(REQUEST_TIMEOUT)
			.setConnectTimeout(CONNECT_TIMEOUT)
			.setSocketTimeout(SOCKET_TIMEOUT).build();
		return HttpClients.custom()
			.setDefaultRequestConfig(requestConfig)
			.setConnectionManager(poolingConnectionManager())
			.setKeepAliveStrategy(connectionKeepAliveStrategy())
			.build();
	}

	@Bean
	public Runnable idleConnectionMonitor(final PoolingHttpClientConnectionManager connectionManager) {
		return new Runnable() {
			@Override
			@Scheduled(fixedDelay = 10000)
			public void run() {
				try {
					if (connectionManager != null) {
						log.trace("run IdleConnectionMonitor - Closing expired and idle connections...");
						connectionManager.closeExpiredConnections();
						connectionManager.closeIdleConnections(CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS, TimeUnit.SECONDS);
					} else {
						log.trace("run IdleConnectionMonitor - Http Client Connection manager is not initialised");
					}
				} catch (Exception e) {
					log.error("run IdleConnectionMonitor - Exception occurred. msg={}, e={}", e.getMessage(), e);
				}
			}
		};
	}

	@Bean
	public HttpComponentsClientHttpRequestFactory clientHttpRequestFactory() {
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
		clientHttpRequestFactory.setHttpClient(httpClient());
		return clientHttpRequestFactory;
	}

	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix("poolScheduler");
		scheduler.setPoolSize(50);
		return scheduler;
	}

	@Bean
	public RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
		return restTemplate;
	}

	@Bean
	public IOSClientBuilder.V3 osClientBuilder() {
		return OSFactory.builderV3()
			.endpoint("https://cf2.cloudferro.com:5000/v3");
	}

	private String readFromInputStream(InputStream inputStream)
		throws IOException {
		StringBuilder resultStringBuilder = new StringBuilder();
		try (BufferedReader br
				 = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = br.readLine()) != null) {
				resultStringBuilder.append(line).append("\n");
			}
		}
		return resultStringBuilder.toString();
	}

	@Bean(name = "version")
	public String version() {
		String version = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			InputStream inputStream = classLoader.getResourceAsStream("version.txt");
			version = readFromInputStream(inputStream);
			version = version.substring(version.lastIndexOf("=") + 1);
			version = version.trim();
		} catch (Exception e) {
			version = "?";
		}
		return version;
	}
}
