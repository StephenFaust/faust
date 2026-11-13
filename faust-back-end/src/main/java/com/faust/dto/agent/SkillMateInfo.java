package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SkillMateInfo {

	/**
	 * 技能编码，agent 内唯一
	 */
	private String skillCode;

	/**
	 * 技能名称
	 */
	private String name;

	/**
	 * 技能描述
	 */
	private String description;


	/**
	 * 内容
	 */
	List<SkillItemMateInfo> skillItemMateInfoList;

}
