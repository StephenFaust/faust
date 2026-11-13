package com.faust.dto.skill.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "技能条目响应")
public class SkillItemResp {

	@Schema(description = "条目类型：CONTENT / SCRIPT / REFERENCE")
	private String itemType;

	@Schema(description = "条目文件名")
	private String fileName;

	@Schema(description = "条目内容")
	private String content;

	@Schema(description = "排序序号")
	private Integer sortOrder;
}
