package com.faust.dto.skill.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建 Skill 请求")
public class SkillCreateReq {

	@NotBlank(message = "skillCode不能为空")
	@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "skillCode 只能由字母、数字和下划线组成，且不能以数字或 _ 开头")
	@Schema(description = "技能编码，全局唯一", example = "log_troubleshoot")
	private String skillCode;

	@NotBlank(message = "name不能为空")
	@Schema(description = "技能名称", example = "日志排查")
	private String name;

	@Schema(description = "触发关键词，逗号分隔", example = "日志,报错,排查")
	private String keyWords;

	@Schema(description = "技能描述")
	private String description;

	@Schema(description = "技能条目列表")
	@Valid
	private List<SkillItemReq> skillItemList;
}
