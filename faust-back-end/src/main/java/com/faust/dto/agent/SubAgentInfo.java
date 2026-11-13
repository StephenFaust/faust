package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubAgentInfo {
	private Long agentId;
	private String agentCode;
	private String version;
}
