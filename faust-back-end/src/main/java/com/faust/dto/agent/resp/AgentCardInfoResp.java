package com.faust.dto.agent.resp;

import lombok.Data;

import java.util.List;

@Data
public class AgentCardInfoResp {

	private Long agentId;

	private String agentCode;

	private String name;

	private String description;

	private List<String> tags;

	private String classification;

	private String version;

	private Integer useNum;
}
