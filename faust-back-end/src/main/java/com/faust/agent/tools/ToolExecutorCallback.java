package com.faust.agent.tools;

import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * 直接把模型入参交给 {@link ToolExecutor} 的工具回调。
 * <p>
 * 与 Spring AI 自带的 {@code MethodToolCallback} 的区别：后者按 <b>Java 形参名</b> 从入参里取值
 * （{@code toolInputArguments.get(parameter.getName())}），而回调方法只有一个形参，
 * 于是入参 Schema 被迫多包一层与形参同名的字段，模型看到的是嵌套结构。
 * 这里跳过反射绑定，把模型输出的 JSON 原样解析成参数表交给执行器，
 * 入参 Schema 就可以是扁平的：模型的 {@code {"sql": "..."}} / {@code {"userId": 1}} 都能直接落到执行器手里。
 * </p>
 * <p>
 * 实现 {@link StateAwareToolCallback} 是为了拿到框架注入的会话上下文：
 * {@code AgentToolNode} 只对 {@code StateAwareToolCallback} / {@code FunctionToolCallback} /
 * {@code MethodToolCallback} 这几类回调往 ToolContext 里补 {@code _AGENT_CONFIG_} 等 key，
 * 子 Agent 工具依赖它取 RunnableConfig，换成普通 ToolCallback 会取到 null。
 * </p>
 */
public final class ToolExecutorCallback implements StateAwareToolCallback {

	private static final Logger logger = LoggerFactory.getLogger(ToolExecutorCallback.class);

	/**
	 * 兼容历史写法：早期 Schema 要求模型包一层 params
	 */
	private static final String LEGACY_PARAMS = "params";

	private final ToolDefinition toolDefinition;

	private final ToolExecutor toolExecutor;

	private final ToolCallResultConverter resultConverter;

	/**
	 * 工具声明过的参数名，用于判断单 key 的 params 是「多包了一层」还是「本来就叫 params」
	 */
	private final Collection<String> declaredParamNames;


	public ToolExecutorCallback(ToolDefinition toolDefinition, AbstractToolExecutor toolExecutor) {
		this(toolDefinition, toolExecutor, new DefaultToolCallResultConverter());
	}


	public ToolExecutorCallback(ToolDefinition toolDefinition, AbstractToolExecutor toolExecutor,
								ToolCallResultConverter resultConverter) {
		Assert.notNull(toolDefinition, "toolDefinition cannot be null");
		Assert.notNull(toolExecutor, "toolExecutor cannot be null");
		this.toolDefinition = toolDefinition;
		this.toolExecutor = toolExecutor;
		this.resultConverter = resultConverter == null ? new DefaultToolCallResultConverter() : resultConverter;
		this.declaredParamNames = toolExecutor.getParamNames();
	}


	@Override
	public @NonNull ToolDefinition getToolDefinition() {
		return this.toolDefinition;
	}


	@Override
	public @NonNull String call(@NonNull String toolInput) {
		return call(toolInput, null);
	}


	@Override
	public @NonNull String call(@NonNull String toolInput, @Nullable ToolContext toolContext) {
		if (toolContext == null) {
			throw new IllegalArgumentException("ToolContext is required by the tool as an argument");
		}
		logger.debug("Starting execution of tool: {}", this.toolDefinition.name());
		Map<String, Object> args = parseArguments(toolInput);
		Object result = this.toolExecutor.execute(args, toolContext);
		logger.debug("Successful execution of tool: {}", this.toolDefinition.name());
		// 与 MethodToolCallback 保持一致：执行器统一返回字符串
		return this.resultConverter.convert(result, String.class);
	}


	/**
	 * 解析模型入参为参数表，并兜底拆掉历史遗留的 params 包裹。
	 */
	private Map<String, Object> parseArguments(String toolInput) {
		if (!StringUtils.hasText(toolInput)) {
			return new HashMap<>();
		}
		Map<String, Object> args;
		try {
			JSONObject json = JSON.parseObject(toolInput);
			args = json == null ? new HashMap<>() : new HashMap<>(json.getInnerMap());
		} catch (Exception e) {
			logger.warn("Conversion from JSON failed", e);
			throw new ToolExecutionException(this.toolDefinition, e);
		}
		Object nested = args.get(LEGACY_PARAMS);
		if (args.size() == 1 && nested instanceof Map<?, ?> nestedMap && !this.declaredParamNames.contains(LEGACY_PARAMS)) {
			Map<String, Object> unwrapped = new HashMap<>(nestedMap.size());
			nestedMap.forEach((key, value) -> unwrapped.put(String.valueOf(key), value));
			return unwrapped;
		}
		return args;
	}


	@Override
	public String toString() {
		return "ToolExecutorCallback{toolDefinition=" + this.toolDefinition + '}';
	}

}
