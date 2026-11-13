package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作空间类型枚举，对应 work_space.space_type
 */
@AllArgsConstructor
@Getter
public enum SpaceTypeEnum {

	/**
	 * 个人空间：仅所有者自己，系统默认生成，不可解散、不可加人
	 */
	PERSONAL("PERSONAL", "个人空间"),

	/**
	 * 团队空间：可新建、可加人
	 */
	TEAM("TEAM", "团队空间");

	private final String value;
	private final String label;

	public static SpaceTypeEnum fromValue(String value) {
		for (SpaceTypeEnum type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		return null;
	}
}
