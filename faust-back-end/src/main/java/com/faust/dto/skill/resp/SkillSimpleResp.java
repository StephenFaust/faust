package com.faust.dto.skill.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent 详情中回显的已绑定技能简要信息
 */
@Data
@Schema(description = "技能简要信息")
public class SkillSimpleResp {

	@Schema(description = "技能 ID")
	private Long skillId;

	@Schema(description = "技能编码")
	private String skillCode;

	@Schema(description = "技能名称")
	private String name;

	@Schema(description = "技能描述")
	private String description;
}
