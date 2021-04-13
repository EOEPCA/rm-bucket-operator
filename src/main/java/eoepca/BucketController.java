package eoepca;

import eoepca.crd.BucketResource;
import eoepca.crd.BucketResourceDoneable;
import eoepca.crd.BucketResourceList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static eoepca.Tracer.Throwing;

@RestController
@Log4j2
public class BucketController {

	@Autowired
	private NonNamespaceOperation<BucketResource, BucketResourceList, BucketResourceDoneable, Resource<BucketResource, BucketResourceDoneable>> bucketClient;

	@Autowired
	private SecretService secretService;

	@GetMapping("/buckets")
	public ResponseEntity<List<Bucket>> getBuckets(@RequestParam("bucket-name") Optional<String> bucketName) {
		return Throwing("getBuckets", bucketName, () -> ResponseEntity.ok(bucketClient.list().getItems().stream()
			.filter(br -> (!bucketName.isPresent()) || br.getSpec().getBucketName().equalsIgnoreCase(bucketName.get()))
			.map(br -> new Bucket(br.getMetadata().getName(), br.getSpec().getBucketName(), br.getSpec().getSecretName(), br.getSpec().getSecretNamespace(), secretService.getBucketSecret(br).map(s -> s.getMetadata().getCreationTimestamp()).orElse(null)))
			.collect(Collectors.toList())));
	}
}
