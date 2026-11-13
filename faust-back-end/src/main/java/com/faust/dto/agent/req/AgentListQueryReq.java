package com.faust.dto.agent.req;

import lombok.Data;

import java.util.List;

@Data
public class AgentListQueryReq {

	private String keyword;

	private List<String> tags;

	private List<String> status;

	private String classification;

	private Long current = 1L;

	private Long size = 20L;
}
