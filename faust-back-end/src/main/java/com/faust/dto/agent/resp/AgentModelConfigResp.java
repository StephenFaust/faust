package com.faust.dto.agent.resp;

import lombok.Data;

@Data
public class AgentModelConfigResp {
	/**
	 * 模型 API 基础 URL
	 */
	private String baseUrl;


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
	private double temperature;

	/**
	 * Top-P 采样参数，0.00-1.00
	 */
	private double topP;

	/**
	 * 请求超时时间（秒）
	 */
	private Integer timeout;
}
