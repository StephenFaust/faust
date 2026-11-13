package com.faust.dto.skill.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Skill 分页查询条件")
public class SkillListQueryReq {

	@Schema(description = "关键字，匹配 skillCode 或 name")
	private String keyword;

	@Schema(description = "状态过滤：DRAFT / PUBLISHED / UNPUBLISHED")
	private List<String> status;

	@Schema(description = "页码，从 1 开始", example = "1")
	private Long current = 1L;

	@Schema(description = "每页条数", example = "20")
	private Long size = 20L;
}
