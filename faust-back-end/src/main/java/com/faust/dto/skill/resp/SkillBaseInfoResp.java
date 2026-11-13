package com.faust.dto.skill.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "技能基础信息")
public class SkillBaseInfoResp {

	@Schema(description = "技能 ID")
	private Long skillId;

	@Schema(description = "技能编码")
	private String skillCode;

	@Schema(description = "技能名称")
	private String name;
}
