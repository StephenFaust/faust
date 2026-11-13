package com.faust.dto.agent.resp;

import lombok.Data;

@Data
public class AgentToolSimpleResp {


	/**
	 * 工具编码，全局唯一
	 */
	private String toolCode;

	/**
	 * 工具id
	 */
	private Long toolId;

	/**
	 * 工具名称
	 */
	private String name;
}
