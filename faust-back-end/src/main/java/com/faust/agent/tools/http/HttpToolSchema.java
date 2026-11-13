package com.faust.agent.tools.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.faust.enums.ParamSourceEnum;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.*;


/**
 * 解析 HTTP 工具 Schema，将 JSON Schema 扩展格式转换为可执行的结构。
 * <p>
 * Schema 扩展格式示例：
 * <pre>
 * {
 *   "name": "query_user",
 *   "description": "查询用户信息",
 *   "url": "https://api.example.com/users/{userId}",
 *   "method": "GET",
 *   "headers": {
 *     "Authorization": "Bearer ${env.ACCESS_TOKEN}",
 *     "X-Biz-Line": "${env.biz_line}"
 *   },
 *   "parameters": {
 *     "userId": {
 *       "type": "string",
 *       "required": true,
 *       "in": "path",
 *       "description": "用户ID"
 *     },
 *     "name": {
 *       "type": "string",
 *       "required": false,
 *       "in": "query",
 *       "description": "用户名"
 *     },
 *     "token": {
 *       "type": "string",
 *       "in": "header",
 *       "source": "ENV",
 *       "sourceKey": "ACCESS_TOKEN",
 *       "description": "凭证，由会话环境变量注入，模型传值会被覆盖"
 *     },
 *     "ucid": {
 *       "type": "string",
 *       "in": "header",
 *       "source": "UCID",
 *       "required": true,
 *       "description": "当前登录用户，由上下文注入；未登录时直接报错"
 *     }
 *   },
 *   "requestBody": {
 *     "type": "object",
 *     "properties": {
 *       "title": {
 *         "type": "string",
 *         "required": true,
 *         "description": "标题"
 *       },
 *       "data": {
 *         "type": "object",
 *         "properties": {
 *           "name": {
 *             "type": "string",
 *             "required": true,
 *             "description": "名称"
 *           },
 *           "age": {
 *             "type": "int",
 *             "required": true,
 *             "description": "年纪"
 *           }
 *         }
 *       }
 *     }
 *   },
 *   "response": {
 *     "type": "object",
 *     "properties": {
 *       "code": {
 *         "type": "integer",
 *         "description": "code"
 *       },
 *       "msg": {
 *         "type": "string",
 *         "description": "信息"
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
@Data
@Builder
public class HttpToolSchema {

	private String name;

	private String description;

	private String url;

	private String method;

	private Map<String, String> headers;

	private Map<String, ParamDef> parameters;

	private JSONObject requestBody;

	private JSONObject responseSchema;

	private String rawSchema;


	public static final String GET = "GET";
	public static final String POST = "POST";
	public static final String PATCH = "PATCH";
	public static final String PUT = "PUT";
	public static final String DELETE = "DELETE";


	public static HttpToolSchema parse(String schemaJson) {
		if (StringUtils.isBlank(schemaJson)) {
			throw new IllegalArgumentException("Schema cannot be empty");
		}
		JSONObject root = JSON.parseObject(schemaJson);

		HttpToolSchemaBuilder builder = HttpToolSchema.builder()
			.name(root.getString("name"))
			.description(root.getString("description"))
			.url(root.getString("url"))
			.method(defaultMethod(root.getString("method")))
			.rawSchema(schemaJson);

		JSONObject headersObj = root.getJSONObject("headers");
		if (headersObj != null && !headersObj.isEmpty()) {
			Map<String, String> headers = new HashMap<>();
			for (String key : headersObj.keySet()) {
				headers.put(key, headersObj.getString(key));
			}
			builder.headers(headers);
		}

		JSONObject paramsObj = root.getJSONObject("parameters");
		if (paramsObj != null && !paramsObj.isEmpty()) {
			Map<String, ParamDef> params = new HashMap<>();
			for (String paramName : paramsObj.keySet()) {
				JSONObject paramDef = paramsObj.getJSONObject(paramName);
				String in = paramDef.getString("in");
				ParamDef.ParamDefBuilder paramBuilder = ParamDef.builder()
					.name(paramName)
					.type(paramDef.getString("type"))
					.in(in)
					.description(paramDef.getString("description"))
					.required(paramDef.getBooleanValue("required"))
					.source(resolveSource(paramDef.getString("source")))
					.sourceKey(defaultIfBlank(paramDef.getString("sourceKey"), paramName));

				JSONArray enumArr = paramDef.getJSONArray("enum");
				if (enumArr != null) {
					paramBuilder.enumValues(enumArr.toJavaList(String.class));
				}

				Object defaultValue = paramDef.get("default");
				if (defaultValue != null) {
					paramBuilder.defaultValue(defaultValue);
				}

				params.put(paramName, paramBuilder.build());
			}
			builder.parameters(params);
		}

		JSONObject reqBody = root.getJSONObject("requestBody");
		if (reqBody == null) {
			reqBody = root.getJSONObject("request_body");
		}
		if (reqBody != null) {
			builder.requestBody(reqBody);
		}

		JSONObject resp = root.getJSONObject("response");
		if (resp != null) {
			builder.responseSchema(resp);
		}

		return builder.build();
	}


	/**
	 * 校验必填参数，包含 parameters 与 requestBody 中声明的必填项。
	 * <p>
	 * 调用前需完成上下文注入，因此这里缺失的上下文参数意味着环境变量/登录信息未提供，
	 * 会给出区别于「模型没传」的明确提示。
	 */
	public final String validateParams(Map<String, Object> params) {
		if (getParameters() != null) {
			for (ParamDef paramDef : getParameters().values()) {
				if (paramDef.isRequired()) {
					Object value = params.get(paramDef.getName());
					if (value == null || value.toString().isBlank()) {
						String missing = missingContextMessage(paramDef.getSource(), paramDef.getSourceKey());
						if (missing != null) {
							return missing;
						}
						return "缺少必填参数: " + paramDef.getName()
							+ (paramDef.getDescription() != null ? " (" + paramDef.getDescription() + ")" : "");
					}
				}

				// 如果参数有枚举值，校验
				if (paramDef.getEnumValues() != null && !paramDef.getEnumValues().isEmpty()) {
					Object value = params.get(paramDef.getName());
					if (value != null && !paramDef.getEnumValues().contains(value.toString())) {
						return "参数 " + paramDef.getName() + " 的值 '" + value
							+ "' 不在允许的范围内: " + String.join(", ", paramDef.getEnumValues());
					}
				}
			}
		}

		// 校验 requestBody 中声明的必填字段
		JSONObject reqBody = getRequestBody();
		if (reqBody != null) {
			JSONObject properties = reqBody.getJSONObject("properties");
			if (properties != null) {
				for (String propName : properties.keySet()) {
					JSONObject propDef = properties.getJSONObject(propName);
					if (propDef.getBooleanValue("required")) {
						Object value = params.get(propName);
						if (value == null || value.toString().isBlank()) {
							String missing = missingContextMessage(ParamSourceEnum.getByCode(propDef.getString("source")),
								defaultIfBlank(propDef.getString("sourceKey"), propName));
							if (missing != null) {
								return missing;
							}
							return "缺少必填请求体参数: " + propName
								+ (StringUtils.isNotBlank(propDef.getString("description"))
								? " (" + propDef.getString("description") + ")" : "");
						}
					}
				}
			}
		}

		return null;
	}


	/**
	 * 解析参数取值来源：
	 * <ul>
	 *     <li>显式声明 {@code source} 优先，按声明值解析；</li>
	 * </ul>
	 */
	private static ParamSourceEnum resolveSource(String sourceCode) {
		return ParamSourceEnum.getByCode(sourceCode);
	}


	private static String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.isBlank(value) ? defaultValue : value;
	}


	/**
	 * 上下文参数缺失时的提示语；模型来源的参数返回 null，走通用必填提示。
	 */
	private static String missingContextMessage(ParamSourceEnum source, String sourceKey) {
		if (source == ParamSourceEnum.UCID) {
			return "参数来源为当前登录用户，但未取到登录信息";
		}
		if (source == ParamSourceEnum.ENV) {
			return "缺少环境变量: " + sourceKey + "，请先在会话环境变量中配置";
		}
		return null;
	}


	private static String defaultMethod(String method) {
		if (StringUtils.isBlank(method)) {
			return GET;
		}
		return method.toUpperCase();
	}

	@Data
	@Builder
	public static class ParamDef {

		private String name;

		private String type;

		private String in;

		private String description;

		private boolean required;

		private List<String> enumValues;

		/**
		 * 参数默认值，仅对模型来源（source 为 null 或 LLM）的参数生效。
		 * 保留原始类型（数字/布尔/数组/对象），避免被压成字符串后在请求体中失真。
		 */
		private Object defaultValue;

		/**
		 * 取值来源，见 {@link ParamSourceEnum}
		 */
		private ParamSourceEnum source;

		/**
		 * 上下文取值 key，默认与参数名一致
		 */
		private String sourceKey;
	}

	public List<String> getPathParamNames() {
		if (parameters == null) return Collections.emptyList();
		return parameters.values().stream()
			.filter(p -> "path".equals(p.getIn()))
			.map(ParamDef::getName)
			.toList();
	}

	public List<String> getQueryParamNames() {
		if (parameters == null) return Collections.emptyList();
		return parameters.values().stream()
			.filter(p -> "query".equals(p.getIn()))
			.map(ParamDef::getName)
			.toList();
	}

	public List<String> getHeaderParamNames() {
		if (parameters == null) return Collections.emptyList();
		return parameters.values().stream()
			.filter(p -> "header".equals(p.getIn()))
			.map(ParamDef::getName)
			.toList();
	}

	/**
	 * requestBody 中声明的属性定义，未定义时返回 null。
	 */
	public JSONObject getRequestBodyProperties() {
		if (requestBody == null) {
			return null;
		}
		return requestBody.getJSONObject("properties");
	}
}
