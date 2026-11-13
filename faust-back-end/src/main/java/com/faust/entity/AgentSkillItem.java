package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent技能明细表（子表 - 技能的具体数据项）
 * <p>item_type: CONTENT=内容/文档, SCRIPT=脚本/代码, REFERENCE=引用/链接</p>
 */
@Data
@TableName("agent_skill_item")
public class AgentSkillItem {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 关联 agent_skill.id
	 */
	private Long skillId;

	/**
	 * 数据类型：CONTENT / SCRIPT / REFERENCE
	 */
	private String itemType;

	/**
	 * 条目文件名
	 * <p>REFERENCE / SCRIPT 类型作为可寻址的文件名，供 read_skill("skill://{skillName}/references/{fileName}") 使用</p>
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


	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;

}
