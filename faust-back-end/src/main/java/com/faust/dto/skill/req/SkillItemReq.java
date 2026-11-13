package com.faust.dto.skill.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "技能条目请求")
public class SkillItemReq {

	@NotBlank(message = "itemType不能为空")
	@Schema(description = "条目类型：CONTENT / SCRIPT / REFERENCE", example = "CONTENT")
	private String itemType;

	@NotBlank(message = "fileName不能为空")
	@Schema(description = "条目文件名，含格式后缀", example = "README.md")
	private String fileName;

	@NotBlank(message = "content不能为空")
	@Schema(description = "条目内容（Markdown / 代码 / URL）")
	private String content;

	@Schema(description = "排序序号")
	private Integer sortOrder;
}
