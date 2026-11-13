package com.faust.agent.tools;


import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * 工具执行器。
 * <p>
 * 入参是模型输出的<b>扁平参数表</b>，每个 key 就是入参 Schema 中声明的字段名，
 * 不做任何额外包裹（早期用 {@code MethodToolCallback} 反射取值时要求包一层 {@code params}，
 * 已由 {@link ToolExecutorCallback} 取代）。
 * </p>
 */
public interface ToolExecutor {

	/**
	 * @param args        模型给出的参数表，字段名与 {@link AbstractToolExecutor#getInputSchema()} 中声明的一致
	 * @param toolContext 调用上下文（登录人、会话环境变量等）
	 * @return 返回给模型的工具结果
	 */
	String execute(Map<String, Object> args, ToolContext toolContext);

}
