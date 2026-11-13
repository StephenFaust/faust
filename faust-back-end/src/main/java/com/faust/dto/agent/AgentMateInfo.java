package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentMateInfo {

	/**
	 * Agent编码，同一Agent各版本共享
	 */
	private String agentCode;

	/**
	 * Agent名称
	 */
	private String name;

	/**
	 * Agent描述，最长100字符
	 */
	private String description;

	/**
	 * 系统提示词
	 */
	private String systemPrompt;


	/**
	 * 模型配置（openAi）
	 */
	private AgentModelMateInfo agentModelMateInfo;

	/**
	 * 技能
	 */
	private List<SkillMateInfo> skillMateInfoList;

	/**
	 * 工具
	 */
	private List<ToolMateInfo> toolMateInfoList;

	/**
	 * 工具
	 */
	private List<ToolMateInfo> nativeToolInfoList;
}
