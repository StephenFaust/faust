package com.faust.enums;

/**
 * 工具参数取值来源。
 * <p>
 * 用于 HTTP 工具 Schema 中的参数定义，声明该参数的值由谁提供：
 * <ul>
 *     <li>{@link #LLM}：由模型在调用工具时传入（默认）</li>
 *     <li>{@link #ENV}：从会话上下文的环境变量中注入</li>
 *     <li>{@link #UCID}：从会话上下文中注入当前登录用户</li>
 * </ul>
 * 非 {@link #LLM} 来源的参数，上下文值优先于模型传值，避免模型伪造身份与凭证。
 */
public enum ParamSourceEnum {

	LLM,

	ENV,

	UCID;


	/**
	 * 按名称解析，忽略大小写；空值或未知值一律按 {@link #LLM} 处理，保证老 Schema 兼容。
	 */
	public static ParamSourceEnum getByCode(String code) {
		if (code == null || code.isBlank()) {
			return LLM;
		}
		String trimmed = code.trim();
		for (ParamSourceEnum source : values()) {
			if (source.name().equalsIgnoreCase(trimmed)) {
				return source;
			}
		}
		return LLM;
	}
}
