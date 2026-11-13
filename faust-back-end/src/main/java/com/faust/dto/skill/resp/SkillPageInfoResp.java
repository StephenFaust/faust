package com.faust.dto.skill.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "技能分页列表项")
public class SkillPageInfoResp {

	@Schema(description = "技能 ID")
	private Long skillId;

	@Schema(description = "技能编码")
	private String skillCode;

	@Schema(description = "技能名称")
	private String name;

	@Schema(description = "技能描述")
	private String description;

	@Schema(description = "触发关键词")
	private String keyWords;

	@Schema(description = "状态：DRAFT / PUBLISHED / UNPUBLISHED")
	private String status;

	@Schema(description = "条目数量")
	private Integer itemCount;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;
}
