package com.faust.agent.tools;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.faust.dto.agent.ToolMateInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;


/**
 * 工具执行器基类。
 * <p>
 * 入参 Schema 约定：模型看到的是<b>扁平</b>的参数结构，{@code properties} 直接铺在顶层，
 * 字段名与执行器从 {@code args} 中取值的名字一一对应。回调由 {@link ToolExecutorCallback} 负责，
 * 它把模型输出的 JSON 原样解析成参数表，不做反射绑定，因此这里不需要为了迁就 Java 形参名而多包一层。
 * </p>
 * <p>
 * 各实现按工具类型给出真正有用的结构：HTTP 工具从 Schema 配置动态生成，DB / 子 Agent 只需要一段文本。
 * </p>
 */
public abstract class AbstractToolExecutor implements ToolExecutor {

	protected final ToolMateInfo toolMateInfo;


	protected AbstractToolExecutor(ToolMateInfo toolMateInfo) {
		this.toolMateInfo = toolMateInfo;
	}


	/**
	 * 提供给模型的入参 JSON Schema，用于构建 ToolDefinition
	 *
	 * @return JSON Schema 字符串
	 */
	public abstract String getInputSchema();


	/**
	 * 本工具在 Schema 中声明过的参数名，用于判断模型是否多包了一层（兼容历史写法）。
	 *
	 * @return 参数名集合，默认空
	 */
	protected Collection<String> getParamNames() {
		return Collections.emptySet();
	}


	/**
	 * 只接受一段文本入参的 Schema（DB 的 SQL、子 Agent 的任务描述等）。
	 *
	 * @param name        参数名，执行器按这个名字从 args 中取值
	 * @param description 参数说明，写清约束与格式要求，直接决定模型输出的质量
	 * @return 形如 {"type":"object","properties":{"sql":{"type":"string"}},"required":["sql"]} 的 Schema
	 */
	protected final String textParamSchema(String name, String description) {
		JSONObject property = new JSONObject(true);
		property.put("type", "string");
		if (StringUtils.isNotBlank(description)) {
			property.put("description", description);
		}
		JSONObject properties = new JSONObject(true);
		properties.put(name, property);
		return objectSchema(properties, Collections.singletonList(name));
	}


	/**
	 * 组装一个 object 类型的入参 Schema。
	 *
	 * @param properties 顶层字段定义
	 * @param required   必填字段名，为空时不输出 required
	 * @return JSON Schema 字符串
	 */
	protected final String objectSchema(JSONObject properties, Collection<?> required) {
		JSONObject schema = new JSONObject(true);
		schema.put("type", "object");
		schema.put("properties", properties);
		if (required != null && !required.isEmpty()) {
			JSONArray requiredNames = new JSONArray();
			requiredNames.addAll(required);
			schema.put("required", requiredNames);
		}
		return schema.toJSONString();
	}


	/**
	 * 从参数表里取一段文本参数。
	 * <p>
	 * 优先取约定名；模型偶尔会自行改字段名（query / input / content 等），
	 * 因此只剩一个字符串值时兜底取它，避免整次调用因为字段名不一致而白白失败。
	 *
	 * @param args        模型参数表
	 * @param expectedKey 约定的字段名
	 * @return 取到的文本；没有可用值时返回 null
	 */
	protected static String textArg(Map<String, Object> args, String expectedKey) {
		if (args == null || args.isEmpty()) {
			return null;
		}
		Object value = args.get(expectedKey);
		if (value instanceof String text && StringUtils.isNotBlank(text)) {
			return text;
		}
		String fallback = null;
		for (Map.Entry<String, Object> entry : args.entrySet()) {
			if (!(entry.getValue() instanceof String text) || StringUtils.isBlank(text)) {
				continue;
			}
			if (fallback != null) {
				// 多个候选字段时无法判断意图，交给调用方按原字段名报错
				return null;
			}
			fallback = text;
		}
		return fallback;
	}


	/**
	 * 参数缺失时的统一返回
	 *
	 * @param name 缺失的参数名
	 * @return 错误结果 JSON
	 */
	protected String missingParamResult(String name) {
		return errorResult("缺少必填参数 " + name);
	}


	protected final String errorResult(String message) {
		JSONObject result = new JSONObject();
		result.put("success", false);
		result.put("error", message);
		return result.toJSONString();
	}

}
