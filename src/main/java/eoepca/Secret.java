package eoepca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Secret {

	@JsonProperty("name")
	@NotEmpty
	@Pattern(regexp = "^[a-z0-9-]+$")
	private String name;

	@JsonProperty("bucketName")
	private String bucketName;

	@JsonProperty("secret")
	private Secret secret;

	@JsonProperty("secretNamespace")
	private String secretNamespace;

	@JsonProperty("bindingStatus")
	private String bindingStatus;

	@JsonProperty("bound")
	private DateTime bindingTimestamp;
}
