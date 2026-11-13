package com.faust.dto.skill.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Skill 操作请求（发布 / 下架 / 删除）")
public class SkillIdReq {

	@NotNull(message = "skillId不能为空")
	@Schema(description = "技能 ID")
	private Long skillId;

}
