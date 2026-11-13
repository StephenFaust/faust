package com.faust.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

@Getter
@AllArgsConstructor
public enum ToolTypeEnum {
	HTTP,
	DB,
	NATIVE,
	SUB_AGENT
	;

	public static ToolTypeEnum getByName(String name) {
		return Arrays.stream(values()).filter(e -> Objects.equals(e.name(), name))
				.findFirst().orElseThrow(() -> new IllegalArgumentException("工具类型不存在"));
	}

}
