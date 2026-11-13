package com.faust.dto.agent.resp;

import com.faust.dto.skill.resp.SkillSimpleResp;
import lombok.Data;

import java.util.List;

@Data
public class AgentInfoDetailResp {

	private Long agentId;

	private String agentCode;

	private String status;

	private String statusDesc;

	private String name;

	private String description;

	private List<String> tags;

	private String classification;

	private String version;

	private Boolean canAsSubAgent;

	private String useNum;


	/**
	 * 系统提示词
	 */
	private String systemPrompt;

	/**
	 * 模型配置
	 */
	private AgentModelConfigResp modelConfig;


	/**
	 * 已绑定的技能
	 */
	private List<SkillSimpleResp> skillList;


	/**
	 * 工具列表
	 */
	private List<AgentToolSimpleResp> toolList;
}
