package com.faust.dto.agent.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建 Agent 请求")
public class AgentCreateReq {

	@NotBlank(message = "agentCode不能为空")
	@Pattern(regexp = "^[A-Za-z][A-Za-z_-]*$", message = "agentCode 只能由字母（大小写均可）、下划线和短横线组成，且不能以 _ 或 - 开头")
	@Schema(description = "Agent 编码，同一 Agent 各版本共享", example = "my-agent")
	private String agentCode;

	@NotBlank(message = "name不能为空")
	@Schema(description = "Agent 名称", example = "我的助手")
	private String name;

	@Schema(description = "Agent 描述，最长 100 字符")
	private String description;

	@Schema(description = "标签", example = "[troubleshooting,fast]")
	private List<String> tags;

	@Schema(description = "分类", example = "运维排查")
	private String classification;

	@NotBlank(message = "version不能为空")
	@Schema(description = "用户指定版本号", example = "1.0.0")
	private String version;

	@NotNull(message = "是否能作为子智能体不能为空")
	@Schema(description = "是否能作为子智能体", example = "1.0.0")
	private Boolean canAsSubAgent;

	/**
	 * 提示词
	 */
	@Schema(description = "提示词配置")
	private AgentPromptReq promptReq;

	/**
	 * 模型配置
	 */
	@Schema(description = "模型配置")
	private AgentModelConfigReq modelCreateConfigReq;


	/**
	 * 技能 ID 列表（已发布技能）
	 */
	@Schema(description = "技能 ID 列表，仅可绑定已发布技能")
	private List<Long> skillIdList;


	/**
	 * 工具列表
	 */
	@Schema(description = "工具 ID 列表")
	private List<Long> toolIdList;
}
