package com.faust.dto.tool.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ToolPageInfoResp {


	private Long toolId;


	private String toolCode;

	private String type;

	private String status;

	private String name;


	private String description;


	private List<String> tags;


	private String version;


	private LocalDateTime createTime;
}
