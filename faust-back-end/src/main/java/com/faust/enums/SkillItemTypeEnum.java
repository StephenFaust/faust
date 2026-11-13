package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 技能条目类型枚举，对应 agent_skill_item.item_type 字段
 */
@AllArgsConstructor
@Getter
public enum SkillItemTypeEnum {

	CONTENT("CONTENT", 1),
	SCRIPT("SCRIPT", 2),
	REFERENCE("REFERENCE", 3);

	private final String desc;
	private final Integer code;

}
