package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * skill 状态枚举，对应 agent_skill.status 字段
 */
@AllArgsConstructor
@Getter
public enum SkillStatusEnum {

	DRAFT("DRAFT", "草稿"),
	PUBLISHED("PUBLISHED", "已发布"),
	UNPUBLISHED("UNPUBLISHED", "未发布");

	private final String value;
	private final String label;

	public static SkillStatusEnum fromValue(String value) {
		for (SkillStatusEnum status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		return null;
	}
}
