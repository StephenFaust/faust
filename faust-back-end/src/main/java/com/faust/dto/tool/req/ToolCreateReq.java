package com.faust.dto.tool.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建 Tool 请求")
public class ToolCreateReq {

	@NotBlank(message = "toolCode")
	@Pattern(regexp = "^[A-Za-z][A-Za-z_-]*$", message = "toolCode 只能由字母（大小写均可）、下划线和短横线组成，且不能以 _ 或 - 开头")
	@Schema(description = "tool 编码", example = "mysql-tool")
	private String toolCode;

	@NotBlank(message = "name不能为空")
	@Schema(description = "tool 名称", example = "我的助手")
	private String name;

	@NotBlank(message = "类型不能为空")
	@Schema(description = "tool 类型", example = "HTTP")
	private String type;

	@Schema(description = "tool 描述，最长 100 字符")
	private String description;

	@Schema(description = "标签，逗号分隔", example = "[]")
	private List<String> tags;

	@NotBlank(message = "version不能为空")
	@Schema(description = "用户指定版本号", example = "1.0.0")
	private String version;


	@Schema(description = "schema", example = "{}")
	private String schemaJson;

	@Schema(description = "配置源配置id", example = "{}")
	private Long dataSourceConfigId;

	@Schema(description = "子agentId", example = "{}")
	private Long subAgentId;
}
