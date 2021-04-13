package eoepca;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class VersionContributor implements InfoContributor {

	@Autowired
	@Qualifier("version")
	private String version;

	@Override
	public void contribute(Info.Builder builder) {
		builder.withDetail("version", version);
	}
}
