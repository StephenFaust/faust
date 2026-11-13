package com.faust.dto.tool.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubAgentInfoResp {
	private Long agentId;
	private String name;
	private String agentCode;
	private String version;
	private String desc;
}
