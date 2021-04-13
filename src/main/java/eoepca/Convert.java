package eoepca;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.util.StringUtils;

import java.util.Base64;

public class Convert {
	public static String toStr(Object object) {
		if (object == null)
			return "";
		if (object instanceof String)
			return (String) object;
		return ToStringBuilder.reflectionToString(object, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public static String toStr(Object... objects) {
		if (objects == null
			|| objects.length == 0)
			return "";
		if (objects.length == 1)
			return toStr(objects[0]);
		return ToStringBuilder.reflectionToString(objects, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	public static String toDate(DateTime dateTime) {
		return ISODateTimeFormat.date().withZoneUTC().print(dateTime);
	}

	public static String toDateTime(DateTime dateTime) {
		return ISODateTimeFormat.dateTimeNoMillis().withZoneUTC().print(dateTime);
	}

	public static String forId(String str) {
		return str.toLowerCase().replaceAll("\\s+", "");
	}

	public static String forHumans(String str) {
		return StringUtils.capitalize(str.replaceAll("_", " "));
	}

	public static String base64Encode(String str) {
		return Base64.getEncoder().encodeToString(str.getBytes());
	}

	public static String base64Decode(String str) {
		return new String(Base64.getDecoder().decode(str));
	}
}

