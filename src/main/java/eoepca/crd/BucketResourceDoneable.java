package eoepca.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import lombok.Getter;

@Getter
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BucketResourceDoneable extends CustomResourceDoneable<BucketResource> {

	public BucketResourceDoneable(BucketResource resource, Function<BucketResource, BucketResource> function) {
		super(resource, function);
	}
}
