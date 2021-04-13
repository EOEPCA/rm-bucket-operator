package eoepca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Bucket {

	@JsonProperty("name")
	@NotEmpty
	@Pattern(regexp = "^[a-z0-9-]+$")
	private String name;

	@JsonProperty("bucketName")
	private String bucketName;

	@JsonProperty("secretName")
	private String secretName;

	@JsonProperty("secretNamespace")
	private String secretNamespace;

	@JsonProperty("creationTimestamp")
	private String creationTimestamp;
}
