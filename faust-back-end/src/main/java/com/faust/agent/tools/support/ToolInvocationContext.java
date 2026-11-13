package com.faust.agent.tools.support;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.faust.constant.AgentConstants;
import com.faust.enums.ParamSourceEnum;
import lombok.Getter;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工具执行上下文：承载当前登录人与会话环境变量，供工具在渲染参数前注入上下文值。
 * <p>
 * 数据来源为 {@link RunnableConfig} 的 metadata：
 * <ul>
 *     <li>{@link AgentConstants#UCID}：当前登录用户</li>
 *     <li>{@link AgentConstants#ENV_VAR}：会话环境变量 Map</li>
 * </ul>
 * 支持在 Schema 声明的字符串（URL、固定请求头、默认值）中使用占位符：
 * <pre>
 *     ${ucid}          → 当前登录用户
 *     ${env.xxx}       → 环境变量 xxx
 *     ${xxx}           → 环境变量 xxx（省略 env. 前缀的简写）
 * </pre>
 */
@Getter
public final class ToolInvocationContext {

	private static final String PLACEHOLDER_START = "${";

	private static final String PLACEHOLDER_END = "}";

	private static final String ENV_PREFIX = "env.";

	private static final String UCID_KEY = "ucid";

	private static final ToolInvocationContext EMPTY = new ToolInvocationContext(null, Collections.emptyMap());

	private final String ucId;

	private final Map<String, String> envVar;


	private ToolInvocationContext(String ucId, Map<String, String> envVar) {
		this.ucId = ucId;
		this.envVar = Objects.isNull(envVar) ? Collections.emptyMap() : envVar;
	}


	public static ToolInvocationContext empty() {
		return EMPTY;
	}


	/**
	 * 从工具上下文中构建，取不到 {@link RunnableConfig} 时返回空上下文。
	 */
	public static ToolInvocationContext from(ToolContext toolContext) {
		if (Objects.isNull(toolContext) || Objects.isNull(toolContext.getContext())) {
			return empty();
		}
		Object config = toolContext.getContext().get(AgentConstants.AGENT_CONFIG);
		return config instanceof RunnableConfig runnableConfig ? from(runnableConfig) : empty();
	}


	/**
	 * 从 {@link RunnableConfig} 中构建，metadata 缺失时对应值为空。
	 */
	public static ToolInvocationContext from(RunnableConfig config) {
		if (Objects.isNull(config)) {
			return EMPTY;
		}
		String ucId = config.metadata(AgentConstants.UCID)
			.map(String::valueOf)
			.orElse(null);
		return new ToolInvocationContext(ucId, toEnvVarMap(config.metadata(AgentConstants.ENV_VAR).orElse(null)));
	}


	/**
	 * 按来源取值，取不到返回 null。
	 *
	 * @param source 取值来源
	 * @param key    环境变量 key（来源为 UCID 时忽略）
	 */
	public String resolve(ParamSourceEnum source, String key) {
		if (source == ParamSourceEnum.UCID) {
			return ucId;
		}
		if (source == ParamSourceEnum.ENV) {
			if (key == null || key.isBlank()) {
				return null;
			}
			return envVar.get(key.trim());
		}
		return null;
	}


	/**
	 * 渲染字符串中的占位符，未识别的占位符替换为空串。
	 */
	public String resolvePlaceholders(String template) {
		if (template == null || template.isBlank() || !template.contains(PLACEHOLDER_START)) {
			return template;
		}
		StringBuilder result = new StringBuilder(template.length());
		int cursor = 0;
		while (cursor < template.length()) {
			int start = template.indexOf(PLACEHOLDER_START, cursor);
			if (start < 0) {
				result.append(template, cursor, template.length());
				break;
			}
			int end = template.indexOf(PLACEHOLDER_END, start + PLACEHOLDER_START.length());
			if (end < 0) {
				result.append(template, cursor, template.length());
				break;
			}
			result.append(template, cursor, start);
			String value = resolveExpr(template.substring(start + PLACEHOLDER_START.length(), end));
			if (value != null) {
				result.append(value);
			}
			cursor = end + PLACEHOLDER_END.length();
		}
		return result.toString();
	}


	private String resolveExpr(String expr) {
		String key = expr.trim();
		if (key.isEmpty()) {
			return null;
		}
		if (UCID_KEY.equalsIgnoreCase(key)) {
			return ucId;
		}
		if (key.length() > ENV_PREFIX.length() && key.toLowerCase().startsWith(ENV_PREFIX)) {
			key = key.substring(ENV_PREFIX.length()).trim();
		}
		return envVar.get(key);
	}


	private static Map<String, String> toEnvVarMap(Object value) {
		if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> envVar = new HashMap<>(raw.size());
		raw.forEach((k, v) -> {
			if (Objects.nonNull(k) && Objects.nonNull(v)) {
				envVar.put(String.valueOf(k), String.valueOf(v));
			}
		});
		return envVar;
	}
}
