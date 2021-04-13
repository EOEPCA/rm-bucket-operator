package eoepca;

import eoepca.crd.BucketResource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static eoepca.Convert.base64Encode;
import static eoepca.Convert.toStr;

@Service
@Log4j2
public class SecretService {

	@Autowired
	private KubernetesClient k8sClient;

	public Optional<Secret> getBucketSecret(BucketResource bucketResource) {
		return k8sClient.secrets().inNamespace(bucketResource.getSpec().getSecretNamespace()).list().getItems().stream()
			.filter(s -> s.getMetadata().getName().equalsIgnoreCase(bucketResource.getSpec().getSecretName()))
			.findFirst();
	}

	public boolean createBucketSecret(BucketResource bucketResource, Map<String, String> data) {
		if (data == null
			|| data.isEmpty()) {
			return false;
		}
		String secretName = bucketResource.getSpec().getSecretName();
		String secretNamespace = bucketResource.getSpec().getSecretNamespace();
		Secret secret = k8sClient.secrets().inNamespace(secretNamespace).withName(secretName).get();
		if (secret == null) {
			secret = new SecretBuilder()
				.withNewMetadata()
				.withName(secretName)
				.withNamespace(secretNamespace)
				.endMetadata()
				.withData(new TreeMap<>())
				.withType("Opaque")
				.build();
		} else {
			secret.getData().clear();
		}
		secret.getData().putAll(data.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> base64Encode(e.getValue()), (s1, s2) -> s1)));
		if (log.isDebugEnabled()) log.debug("createOrReplace {}", toStr(secret));
		Secret changedSecret = k8sClient.secrets().inNamespace(secretNamespace).createOrReplace(secret);
		return !EqualsBuilder.reflectionEquals(secret, changedSecret, "metadata");
	}
}
