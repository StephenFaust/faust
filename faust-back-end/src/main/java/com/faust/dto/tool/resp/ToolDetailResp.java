package com.faust.dto.tool.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ToolDetailResp {


	private Long toolId;


	private String toolCode;

	private Boolean isLocalTool;

	private String name;

	private String type;

	private String status;


	private String description;


	private String schemaJson;

	private LocalDateTime createTime;

	private DataSourceConfigInfoResp dataSourceConfig;

	private SubAgentInfoResp subAgentInfoResp;

	private List<String> tags;


	private List<NativeToolsListResp> nativeToolsListResp;

	private String version;

}
