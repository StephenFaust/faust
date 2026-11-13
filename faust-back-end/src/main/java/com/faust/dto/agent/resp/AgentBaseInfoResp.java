package com.faust.dto.agent.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentBaseInfoResp {

	private Long agentId;

	private String agentCode;

	private String name;

	private String version;
}
