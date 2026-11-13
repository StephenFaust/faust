package com.faust.dto.tool.resp;

import lombok.Data;

@Data
public class ToolBaseInfoResp {
	private Long toolId;
	private String toolCode;
	private String name;
	private String version;
}
