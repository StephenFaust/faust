package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * tool 状态枚举，对应 tool.status 字段
 */
@AllArgsConstructor
@Getter
public enum ToolStatusEnum {

	UNPUBLISHED("UNPUBLISHED", "未发布"),
	DRAFT("DRAFT", "草稿"),
	PUBLISHED("PUBLISHED", "已发布");

	private final String value;
	private final String label;

	public static ToolStatusEnum fromValue(String value) {
		for (ToolStatusEnum status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		return null;
	}
}
