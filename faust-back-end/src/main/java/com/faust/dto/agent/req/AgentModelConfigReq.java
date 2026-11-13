package com.faust.dto.agent.req;

import lombok.Data;

@Data
public class AgentModelConfigReq {

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
}
