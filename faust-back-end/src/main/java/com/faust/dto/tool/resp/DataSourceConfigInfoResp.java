package com.faust.dto.tool.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSourceConfigInfoResp {

	private Long configId;
	private String dbName;
	private String dbType;
	private String url;
}
