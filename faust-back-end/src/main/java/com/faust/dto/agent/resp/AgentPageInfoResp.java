package com.faust.dto.agent.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentPageInfoResp {

	private Long agentId;

	private String agentCode;

	private String status;

	private String statusDesc;

	private String name;

	private String description;

	private List<String> tags;

	private String classification;

	private String version;

	private LocalDateTime createTime;

	/** 使用次数：智能体页与工作台合并后，列表也要展示热度 */
	private Integer useNum;
}
