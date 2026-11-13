package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent模型配置表（与 agent 1:1 关联）
 */
@Data
@TableName("agent_model_config")
public class AgentModelConfig {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 关联 agent_info.id
	 */
	private Long agentId;

	/**
	 * 模型 API 基础 URL
	 */
	private String baseUrl;

	/**
	 * API 密钥
	 */
	private String apiKey;

	/**
	 * 模型名称，如 gpt-4、qwen-max
	 */
	private String modelName;

	/**
	 * 最大 Token 数
	 */
	private Integer maxTokens;

	/**
	 * 温度参数，0.00-1.00
	 */
	private Double temperature;

	/**
	 * Top-P 采样参数，0.00-1.00
	 */
	private Double topP;

	/**
	 * 请求超时时间（秒）
	 */
	private Integer timeout;


	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long createBy;

	private Long updateBy;

}
