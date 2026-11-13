package com.faust.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "agent.center.file")
@Configuration
@Data
public class FileProperties {
	private String bucket;
	private String ak;
	private String sk;
	private String url;
	private String outUrl;
	private String imgUrl;
	private Integer expireMin = 1440;
	private String signingRegion;
}
