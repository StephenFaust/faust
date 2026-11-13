package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作空间成员角色，对应 work_space_user.role
 * <p>OWNER：创建者，可重命名 / 移除成员 / 解散；MEMBER：可加人、可退出。</p>
 */
@AllArgsConstructor
@Getter
public enum SpaceRoleEnum {

	OWNER("OWNER", "创建者"),
	MEMBER("MEMBER", "成员");

	private final String value;
	private final String label;

	public static SpaceRoleEnum fromValue(String value) {
		for (SpaceRoleEnum role : values()) {
			if (role.value.equals(value)) {
				return role;
			}
		}
		return null;
	}
}
