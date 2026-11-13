package com.faust.agent.tools.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.faust.dto.agent.ToolMateInfo;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.ParamSourceEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.agent.tools.AbstractToolExecutor;
import com.faust.agent.tools.support.ToolInvocationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.faust.agent.tools.http.HttpToolSchema.*;


/**
 * HTTP 工具执行器，根据 {@link HttpToolSchema} 动态构建并执行 HTTP 请求。
 */
@Slf4j
public final class HttpToolExecutor extends AbstractToolExecutor {

	private final HttpClient httpClient;
	private final HttpToolSchema httpToolSchema;

	public HttpToolExecutor(HttpClient httpClient, ToolMateInfo toolMateInfo) {
		super(toolMateInfo);
		this.httpClient = httpClient;
		this.httpToolSchema = HttpToolSchema.parse(toolMateInfo.getToolSchema());
	}


	/**
	 * 由 HTTP 工具 Schema 动态生成扁平入参 Schema，职责是「只把模型该填的参数告诉模型」：
	 * <ul>
	 *     <li>parameters 中 in=path / query / header 的参数逐项平铺，保留类型、说明、枚举、默认值与必填；</li>
	 *     <li>requestBody.properties 与 parameters 平铺在同一层，执行器按同名取值组装请求体（支持嵌套 object / array）；</li>
	 *     <li>source 为 ENV / UCID 的参数由运行时上下文注入，模型填了也会被覆盖，因此不暴露，避免模型伪造身份与凭证。</li>
	 * </ul>
	 */
	@Override
	public String getInputSchema() {
		JSONObject properties = new JSONObject(true);
		JSONArray required = new JSONArray();

		// 1. 顶层 parameters
		if (httpToolSchema.getParameters() != null) {
			for (HttpToolSchema.ParamDef paramDef : httpToolSchema.getParameters().values()) {
				if (isContextParam(paramDef.getSource())) {
					continue;
				}
				properties.put(paramDef.getName(), buildParameterSchema(paramDef));
				if (paramDef.isRequired()) {
					required.add(paramDef.getName());
				}
			}
		}

		// 2. requestBody 属性
		JSONObject bodyProperties = httpToolSchema.getRequestBodyProperties();
		if (bodyProperties != null) {
			for (String propName : bodyProperties.keySet()) {
				JSONObject propDef = bodyProperties.getJSONObject(propName);
				if (propDef == null || isContextProperty(propDef)) {
					continue;
				}
				properties.put(propName, buildPropertySchema(propDef));
				if (propDef.getBooleanValue("required")) {
					required.add(propName);
				}
			}
		}

		return objectSchema(properties, required);
	}


	/**
	 * 本工具实际声明的参数名，供回调判断模型是否多包了一层 params
	 */
	@Override
	protected Collection<String> getParamNames() {
		Set<String> names = new HashSet<>();
		if (httpToolSchema.getParameters() != null) {
			httpToolSchema.getParameters().values().forEach(paramDef -> names.add(paramDef.getName()));
		}
		JSONObject bodyProperties = httpToolSchema.getRequestBodyProperties();
		if (bodyProperties != null) {
			names.addAll(bodyProperties.keySet());
		}
		return names;
	}


	/**
	 * 上下文来源（env / ucid）的参数不交给模型填
	 */
	private static boolean isContextParam(ParamSourceEnum source) {
		return source != null && source != ParamSourceEnum.LLM;
	}


	/**
	 * 判断一个属性定义（properties 下的 JSON 节点）是否为上下文来源
	 */
	private static boolean isContextProperty(JSONObject propDef) {
		return propDef != null && isContextParam(ParamSourceEnum.getByCode(propDef.getString("source")));
	}


	/**
	 * 单个 parameter 定义 → JSON Schema 片段，位置信息并进 description（模型据此知道参数放在哪）
	 */
	private JSONObject buildParameterSchema(HttpToolSchema.ParamDef paramDef) {
		JSONObject schema = schemaOfType(paramDef.getType());
		String description = joinDescription(paramDef.getDescription(), paramLocationHint(paramDef.getIn()));
		if (StringUtils.isNotBlank(description)) {
			schema.put("description", description);
		}
		if (paramDef.getEnumValues() != null && !paramDef.getEnumValues().isEmpty()) {
			schema.put("enum", paramDef.getEnumValues());
		}
		if (paramDef.getDefaultValue() != null) {
			schema.put("default", paramDef.getDefaultValue());
		}
		return schema;
	}


	/**
	 * requestBody 属性 → JSON Schema 片段，递归处理嵌套 object 与 array
	 */
	private JSONObject buildPropertySchema(JSONObject propDef) {
		String type = normalizeType(propDef.getString("type"));
		JSONObject schema = new JSONObject(true);
		schema.put("type", type);

		String description = propDef.getString("description");
		if (StringUtils.isNotBlank(description)) {
			schema.put("description", description);
		}
		JSONArray enumValues = propDef.getJSONArray("enum");
		if (enumValues != null && !enumValues.isEmpty()) {
			schema.put("enum", enumValues);
		}
		JSONObject nestedProperties = propDef.getJSONObject("properties");
		if (nestedProperties != null && !nestedProperties.isEmpty()) {
			schema.put("type", "object");
			schema.put("properties", buildNestedProperties(nestedProperties));
			JSONArray nestedRequired = collectRequired(nestedProperties);
			if (!nestedRequired.isEmpty()) {
				schema.put("required", nestedRequired);
			}
		}
		Object items = propDef.get("items");
		if ("array".equals(schema.getString("type")) && items != null) {
			schema.put("items", items instanceof JSONObject itemsDef
					? buildPropertySchema(itemsDef)
					: schemaOfType(String.valueOf(items)));
		}
		Object defaultValue = propDef.get("default");
		if (defaultValue != null) {
			schema.put("default", defaultValue);
		}
		return schema;
	}


	private JSONObject buildNestedProperties(JSONObject nestedProperties) {
		JSONObject childSchemas = new JSONObject(true);
		for (String childName : nestedProperties.keySet()) {
			JSONObject childDef = nestedProperties.getJSONObject(childName);
			// 嵌套层级同样过滤上下文来源，否则模型会看到不该由它填的字段
			if (childDef == null || isContextProperty(childDef)) {
				continue;
			}
			childSchemas.put(childName, buildPropertySchema(childDef));
		}
		return childSchemas;
	}


	private JSONArray collectRequired(JSONObject properties) {
		JSONArray required = new JSONArray();
		for (String name : properties.keySet()) {
			JSONObject propDef = properties.getJSONObject(name);
			if (propDef == null || isContextProperty(propDef)) {
				continue;
			}
			if (propDef.getBooleanValue("required")) {
				required.add(name);
			}
		}
		return required;
	}


	private JSONObject schemaOfType(String type) {
		JSONObject schema = new JSONObject(true);
		schema.put("type", normalizeType(type));
		return schema;
	}


	/**
	 * Schema 里的类型是自定义写法（int / bool / double ...），统一收敛成 JSON Schema 的六种基础类型
	 */
	private static String normalizeType(String type) {
		if (StringUtils.isBlank(type)) {
			return "string";
		}
		return switch (type.trim().toLowerCase(Locale.ROOT)) {
			case "int", "integer", "long", "short", "byte", "bigint" -> "integer";
			case "number", "double", "float", "decimal", "bigdecimal" -> "number";
			case "bool", "boolean" -> "boolean";
			case "array", "list", "set", "collection" -> "array";
			case "object", "map", "json", "jsonobject" -> "object";
			default -> "string";
		};
	}


	private static String joinDescription(String description, String hint) {
		if (StringUtils.isBlank(hint)) {
			return description;
		}
		if (StringUtils.isBlank(description)) {
			return hint;
		}
		return description + hint;
	}


	/**
	 * 参数位置提示，帮助模型理解同名参数在请求中的位置
	 */
	private static String paramLocationHint(String in) {
		if (StringUtils.isBlank(in)) {
			return null;
		}
		return switch (in.trim().toLowerCase(Locale.ROOT)) {
			case "path" -> "（路径参数）";
			case "query" -> "（查询参数）";
			case "header" -> "（请求头参数）";
			case "body" -> "（请求体参数）";
			default -> null;
		};
	}

	/**
	 * 执行一个 HTTP 工具调用。
	 * <p>
	 * 链路：解析参数 → 构建上下文 → 注入上下文参数 → 校验必填 → 渲染并执行请求。
	 * 注入必须在校验之前，否则来源于环境变量/登录人的必填参数会被误判为缺失。
	 *
	 * @param args        实际参数值（key=参数名，value=参数值），与入参 Schema 的扁平字段一一对应
	 * @param toolContext 上下文
	 * @return 执行结果 JSON 字符串
	 */
	@Override
	public String execute(Map<String, Object> args, ToolContext toolContext) {
		String toolCode = toolMateInfo.getToolCode();
		try {
			// 1. 取出模型传入的参数（模型漏传时兜底为空表，避免后续 NPE；复制一份避免改动调用方集合）
			Map<String, Object> paramMap = args == null ? new HashMap<>() : new HashMap<>(args);
//			parseParams(params);
			// 2. 构建执行上下文（登录人 + 会话环境变量）
			ToolInvocationContext context = ToolInvocationContext.from(toolContext);
			// 3. 注入上下文参数（env / ucid），上下文值优先于模型传值
			injectContextParams(httpToolSchema, context, paramMap);
			// 3.1 为模型来源参数填充 schema 声明的默认值（在必填校验之前，使默认值替必填参数过关）
			applyParameterDefaults(httpToolSchema, paramMap);
			// 4. 校验必填参数
			String validationError = httpToolSchema.validateParams(paramMap);
			if (validationError != null) {
				return errorResult("参数校验失败: " + validationError);
			}
			// 5. 执行 HTTP 请求（日志中上下文来源参数脱敏，避免凭证/登录信息明文落盘）
			LOGGER.info("执行 HTTP 工具: toolCode={}, method={}, url={}, params={}",
					toolCode, httpToolSchema.getMethod(), httpToolSchema.getUrl(), maskContextParams(httpToolSchema, paramMap));
			return execute(httpToolSchema, paramMap, context);
		} catch (Exception e) {
			LOGGER.error("执行 HTTP 工具异常: toolCode={}", toolCode, e);
			return errorResult("执行异常: " + e.getMessage());
		}
	}


	/**
	 * 将上下文（环境变量 / 当前登录用户）中的值注入参数表。
	 * 覆盖 parameters 中的 path、query、header 参数，以及 requestBody 中声明的属性。
	 */
	private void injectContextParams(HttpToolSchema schema, ToolInvocationContext context, Map<String, Object> params) {
		injectFromParameters(schema, context, params);
		injectFromRequestBody(schema, context, params);
	}


	/**
	 * 为模型来源的（source 为 null 或 LLM）缺失参数填充 schema 中声明的默认值。
	 * <p>
	 * 在上下文注入之后、必填校验之前执行，使默认值能够替必填参数"过关"。
	 * 仅在参数缺失（key 不存在或值为 null）时生效，模型显式传值（含空串）被尊重。
	 * 上下文来源参数（env / ucid）不由默认值填充，它们由 {@link #injectContextParams} 处理。
	 */
	private void applyParameterDefaults(HttpToolSchema schema, Map<String, Object> params) {
		// 1. 顶层 parameters
		if (schema.getParameters() != null) {
			for (HttpToolSchema.ParamDef paramDef : schema.getParameters().values()) {
				ParamSourceEnum source = paramDef.getSource();
				if (source != null && source != ParamSourceEnum.LLM) {
					continue;
				}
				Object defaultValue = paramDef.getDefaultValue();
				if (defaultValue == null) {
					continue;
				}
				if (params.get(paramDef.getName()) == null) {
					params.put(paramDef.getName(), defaultValue);
					LOGGER.debug("参数 {} 使用默认值: {}", paramDef.getName(), defaultValue);
				}
			}
		}

		// 2. requestBody 属性（默认值同样需在必填校验前入参，否则必填 + 默认仍会校验失败）
		JSONObject properties = schema.getRequestBodyProperties();
		if (properties != null) {
			for (String propName : properties.keySet()) {
				JSONObject propDef = properties.getJSONObject(propName);
				if (propDef == null) {
					continue;
				}
				ParamSourceEnum source = ParamSourceEnum.getByCode(propDef.getString("source"));
				if (source != null && source != ParamSourceEnum.LLM) {
					continue;
				}
				if (params.get(propName) != null) {
					continue;
				}
				Object defaultValue = propDef.get("default");
				if (defaultValue != null) {
					params.put(propName, defaultValue);
					LOGGER.debug("请求体属性 {} 使用默认值: {}", propName, defaultValue);
				}
			}
		}
	}


	private void injectFromParameters(HttpToolSchema schema, ToolInvocationContext context, Map<String, Object> params) {
		if (schema.getParameters() == null) {
			return;
		}
		for (HttpToolSchema.ParamDef paramDef : schema.getParameters().values()) {
			ParamSourceEnum source = paramDef.getSource();
			if (source == null || source == ParamSourceEnum.LLM) {
				continue;
			}
			String value = context.resolve(source, paramDef.getSourceKey());
			putContextValue(params, paramDef.getName(), source, paramDef.getSourceKey(), value, paramDef.isRequired());
		}
	}


	private void injectFromRequestBody(HttpToolSchema schema, ToolInvocationContext context, Map<String, Object> params) {
		JSONObject properties = schema.getRequestBodyProperties();
		if (properties == null) {
			return;
		}
		for (String propName : properties.keySet()) {
			JSONObject propDef = properties.getJSONObject(propName);
			if (propDef == null) {
				continue;
			}
			ParamSourceEnum source = ParamSourceEnum.getByCode(propDef.getString("source"));
			if (source == ParamSourceEnum.LLM) {
				continue;
			}
			String sourceKey = StringUtils.isBlank(propDef.getString("sourceKey"))
					? propName : propDef.getString("sourceKey").trim();
			String value = context.resolve(source, sourceKey);
			putContextValue(params, propName, source, sourceKey, value, propDef.getBooleanValue("required"));
		}
	}


	/**
	 * 写入上下文值，上下文值覆盖模型传值；必填且来源为登录人时直接抛未登录。
	 */
	private void putContextValue(Map<String, Object> params, String name, ParamSourceEnum source,
								 String sourceKey, String value, boolean required) {
		if (StringUtils.isBlank(value)) {
			if (required && source == ParamSourceEnum.UCID) {
				throw new AgentBusinessException(ErrorMessageEnum.NO_LOGIN_USER.getErrorCode(),
						ErrorMessageEnum.NO_LOGIN_USER.getErrorMsg());
			}
			if (required) {
				LOGGER.warn("工具参数无法从上下文注入: param={}, source={}, sourceKey={}", name, source, sourceKey);
			}
			return;
		}
		Object origin = params.put(name, value);
		if (origin != null && !value.equals(origin.toString())) {
			LOGGER.warn("参数 {} 由上下文覆盖模型传值: {} -> {}", name, origin, value);
		}
	}


	/**
	 * 构造用于日志的参数副本，对来源于上下文（环境变量 / 登录人）的敏感值做脱敏，
	 * 避免凭证、登录信息落到日志明文。模型来源的普通参数保持原值。
	 */
	private Map<String, Object> maskContextParams(HttpToolSchema schema, Map<String, Object> params) {
		Map<String, Object> masked = new HashMap<>(params);
		if (schema.getParameters() != null) {
			schema.getParameters().values().stream()
					.filter(p -> p.getSource() != null && p.getSource() != ParamSourceEnum.LLM)
					.forEach(p -> masked.put(p.getName(), "***"));
		}
		JSONObject properties = schema.getRequestBodyProperties();
		if (properties != null) {
			properties.forEach((key, value) -> {
				if (value instanceof JSONObject propDef
						&& ParamSourceEnum.UCID == ParamSourceEnum.getByCode(propDef.getString("source"))) {
					masked.put(key, "***");
				}
			});
		}
		return masked;
	}


	/**
	 * 执行一个 HTTP 工具调用。
	 *
	 * @param schema  工具 Schema
	 * @param params  实际参数值（key=参数名，value=参数值）
	 * @param context 执行上下文（登录人 + 环境变量）
	 * @return 执行结果 JSON 字符串
	 */
	private String execute(HttpToolSchema schema, Map<String, Object> params, ToolInvocationContext context) {
		long start = System.currentTimeMillis();
		try {
			// 1. 构建最终 URL（占位符渲染 + 路径参数替换 + 拼接 query 参数）
			String resolvedUrl = resolveUrl(schema, params, context);

			// 2. 构建请求头
			Map<String, String> resolvedHeaders = resolveHeaders(schema, params, context);

			// 3. 构建请求体
			byte[] body = resolveBody(schema, params);

			// 4. 构建 HttpRequest
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(resolvedUrl))
					.timeout(Duration.ofSeconds(30));

			// 设置请求头
			for (Map.Entry<String, String> entry : resolvedHeaders.entrySet()) {
				requestBuilder.header(entry.getKey(), entry.getValue());
			}

			// 设置请求方法和 body
			String method = schema.getMethod().toUpperCase();
			switch (method) {
				case GET:
					requestBuilder.GET();
					break;
				case DELETE:
					requestBuilder.DELETE();
					break;
				case POST:
					requestBuilder.POST(BodyPublishers.ofByteArray(body));
					break;
				case PUT:
					requestBuilder.PUT(BodyPublishers.ofByteArray(body));
					break;
				case PATCH:
					requestBuilder.method(PATCH, BodyPublishers.ofByteArray(body));
					break;
				default:
					return errorResult("不支持的 HTTP 方法: " + method);
			}

			// 5. 发送请求
			HttpRequest request = requestBuilder.build();
			LOGGER.debug("HTTP Tool 请求: {} {}, headers={}, body={}",
					method, resolvedUrl, resolvedHeaders, body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "<empty>");

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			long cost = System.currentTimeMillis() - start;

			// 6. 处理响应
			int statusCode = response.statusCode();
			String responseBody = response.body();

			LOGGER.debug("HTTP Tool 响应: status={}, cost={}ms, bodyLength={}", statusCode, cost, responseBody.length());

			// 7. 构建返回结果
			JSONObject result = new JSONObject();
			result.put("status", statusCode);
			result.put("cost_ms", cost);

			if (statusCode >= 200 && statusCode < 300) {
				// 尝试解析 JSON 响应
				Object parsedBody = tryParseJson(responseBody);
				result.put("data", parsedBody);
				// 如果有 response schema，尝试提取结构化的响应
				if (schema.getResponseSchema() != null) {
					result.put("structured_data", extractStructuredResponse(schema, parsedBody));
				}
			} else {
				result.put("error", responseBody);
				result.put("success", false);
			}

			return result.toJSONString();
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			long cost = System.currentTimeMillis() - start;
			LOGGER.error("HTTP Tool 执行异常: {}", e.getMessage(), e);
			return errorResult("HTTP 请求异常: " + e.getMessage() + ", 耗时: " + cost + "ms");
		}
	}


	/**
	 * 解析完整的 URL，包括占位符渲染、路径参数替换和 query 参数拼接。
	 */
	private String resolveUrl(HttpToolSchema schema, Map<String, Object> params, ToolInvocationContext context) {
		String url = context.resolvePlaceholders(schema.getUrl());
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Schema 中 URL 不能为空");
		}
		String resolvedUrl = url;
		for (String pathParam : schema.getPathParamNames()) {
			Object value = params.get(pathParam);
			if (value != null) {
				String encoded = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
				resolvedUrl = resolvedUrl.replace("{" + pathParam + "}", encoded);
			}
		}

		// 拼接 query 参数
		String queryString = schema.getQueryParamNames().stream()
				.filter(paramName -> params.containsKey(paramName) && params.get(paramName) != null)
				.map(paramName -> {
					String encodedKey = URLEncoder.encode(paramName, StandardCharsets.UTF_8);
					String encodedValue = URLEncoder.encode(params.get(paramName).toString(), StandardCharsets.UTF_8);
					return encodedKey + "=" + encodedValue;
				})
				.collect(Collectors.joining("&"));

		if (!queryString.isEmpty()) {
			resolvedUrl = resolvedUrl + (resolvedUrl.contains("?") ? "&" : "?") + queryString;
		}

		return resolvedUrl;
	}

	/**
	 * 解析请求头，包含固定头（支持占位符）+ 参数中 in=header 的动态头。
	 * 上下文参数（env / ucid）已在 {@link #injectContextParams} 中写入 params，此处直接取值。
	 */
	private Map<String, String> resolveHeaders(HttpToolSchema schema, Map<String, Object> params, ToolInvocationContext context) {
		Map<String, String> headers = new HashMap<>();

		// 固定头，值支持 ${env.xxx} / ${ucid} 占位符
		if (schema.getHeaders() != null) {
			schema.getHeaders().forEach((key, value) -> headers.put(key, context.resolvePlaceholders(value)));
		}
		// 动态头（参数中 in=header 的）
		for (String headerParam : schema.getHeaderParamNames()) {
			Object value = params.get(headerParam);
			if (value != null) {
				headers.put(headerParam, value.toString());
			}
		}

		// 如果没设置 Content-Type 且是 POST/PUT/PATCH，默认 JSON
		String method = schema.getMethod().toUpperCase();
		if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
				&& !headers.containsKey("Content-Type")) {
			headers.put("Content-Type", "application/json");
		}

		return headers;
	}


	/**
	 * 解析请求体。
	 * 优先使用 request_body schema 构建 JSON body，否则尝试从参数中查找 body 相关参数。
	 */
	private byte[] resolveBody(HttpToolSchema schema, Map<String, Object> params) {
		String method = schema.getMethod().toUpperCase();
		if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
			return new byte[0];
		}

		// 1. 如果有 requestBody schema，构建 JSON body
		JSONObject reqBodySchema = schema.getRequestBody();
		if (reqBodySchema != null) {
			JSONObject body = buildJsonBody(reqBodySchema, params);
			return body.toJSONString().getBytes(StandardCharsets.UTF_8);
		}

		// 2. 如果参数中有 body 或者 requestBody 参数，直接使用它
		if (params.containsKey("body") && params.get("body") != null) {
			return params.get("body").toString().getBytes(StandardCharsets.UTF_8);
		}
		if (params.containsKey("requestBody") && params.get("requestBody") != null) {
			return params.get("requestBody").toString().getBytes(StandardCharsets.UTF_8);
		}

		// 3. 否则把所有不在 path/query/header 中的参数作为 body
		JSONObject body = new JSONObject();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			String paramName = entry.getKey();
			// 跳过已用于路径/query/header 的参数
			boolean isMeta = schema.getPathParamNames().contains(paramName)
					|| schema.getQueryParamNames().contains(paramName)
					|| schema.getHeaderParamNames().contains(paramName);
			if (!isMeta && entry.getValue() != null) {
				body.put(paramName, entry.getValue());
			}
		}

		return body.toJSONString().getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * 根据 request_body Schema 和参数构建 JSON 对象。
	 */
	private JSONObject buildJsonBody(JSONObject bodySchema, Map<String, Object> params) {
		JSONObject body = new JSONObject();
		JSONObject properties = bodySchema.getJSONObject("properties");
		if (properties == null) {
			// 如果 schema 没有定义 properties，直接将所有参数作为 body
			body.putAll(params);
			return body;
		}

		for (String propName : properties.keySet()) {
			JSONObject propDef = properties.getJSONObject(propName);
			// 默认值已在 applyParameterDefaults 阶段写入 params，此处仅取模型/上下文传值
			if (params.containsKey(propName) && params.get(propName) != null) {
				Object value = convertParamValue(propDef, params.get(propName));
				body.put(propName, value);
			}
		}
		return body;
	}

	/**
	 * 根据参数类型的定义，将参数值转换为合适的类型。
	 */
	private Object convertParamValue(JSONObject propDef, Object value) {
		if (value == null) return null;
		if (propDef == null) return value;

		String type = propDef.getString("type");
		if (type == null) return value;

		String strValue = value.toString();
		return switch (type) {
			case "integer", "int", "long" -> {
				try {
					yield Long.parseLong(strValue);
				} catch (NumberFormatException e) {
					yield value;
				}
			}
			case "number", "double", "float" -> {
				try {
					yield Double.parseDouble(strValue);
				} catch (NumberFormatException e) {
					yield value;
				}
			}
			case "boolean", "bool" -> Boolean.parseBoolean(strValue);
			case "array" -> {
				try {
					yield JSON.parseArray(strValue);
				} catch (Exception e) {
					yield value;
				}
			}
			case "object" -> {
				try {
					yield JSON.parseObject(strValue);
				} catch (Exception e) {
					yield value;
				}
			}
			default -> strValue;
		};
	}

	/**
	 * 尝试解析 JSON 字符串，如果失败返回原始字符串。
	 */
	private Object tryParseJson(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}
		try {
			return JSON.parse(text);
		} catch (Exception e) {
			return text;
		}
	}

	/**
	 * 根据 response schema 提取结构化的响应数据。
	 */
	private Object extractStructuredResponse(HttpToolSchema schema, Object responseBody) {
		JSONObject respSchema = schema.getResponseSchema();
		if (respSchema == null || responseBody == null) {
			return responseBody;
		}

		// 如果响应体是 JSON 对象，提取 properties 中定义的字段
		if (responseBody instanceof JSONObject jsonObj) {
			JSONObject properties = respSchema.getJSONObject("properties");
			if (properties != null) {
				JSONObject structured = new JSONObject();
				for (String key : properties.keySet()) {
					if (jsonObj.containsKey(key)) {
						structured.put(key, jsonObj.get(key));
					}
				}
				return structured;
			}
		}

		return responseBody;
	}

}
