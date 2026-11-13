package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSourceInfo {
	private String dbType;
	private String url;
	private String user;
	private String pwd;
}
