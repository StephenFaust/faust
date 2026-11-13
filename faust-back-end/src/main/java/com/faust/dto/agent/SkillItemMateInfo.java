package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillItemMateInfo {
	/**
	 * 数据类型：CONTENT / SCRIPT / REFERENCE
	 */
	private String itemType;

	/**
	 * 条目文件名
	 * <p>REFERENCE / SCRIPT 类型作为可寻址的文件名</p>
	 */
	private String fileName;

	/**
	 * 条目内容（Markdown / 代码 / URL 等）
	 */
	private String content;

	/**
	 * 排序序号
	 */
	private Integer sortOrder;
}
