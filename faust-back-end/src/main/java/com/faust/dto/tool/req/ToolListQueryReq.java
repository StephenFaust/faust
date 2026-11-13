package com.faust.dto.tool.req;

import lombok.Data;

import java.util.List;

@Data
public class ToolListQueryReq {

	private String keyword;

	private List<String> tags;

	private String type;

	private List<String> status;

	private boolean excludeAgentTool;

	private Long current = 1L;

	private Long size = 20L;
}
