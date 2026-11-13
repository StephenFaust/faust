package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent技能表（技能元数据，独立管理，可发布共享）
 * <p>DRAFT 状态的技能仅用于编辑，PUBLISHED 后所有 agent 可通过 agent_skill_rel 关联。</p>
 * <p>技能无版本概念，同一 skillCode 全局唯一。</p>
 */
@Data
@TableName("agent_skill")
public class AgentSkill {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 所属工作空间，见 work_space.id
	 */
	private Long workspaceId;

	/**
	 * 技能编码，全局唯一
	 */
	private String skillCode;

	/**
	 * 技能名称
	 */
	private String name;

	/**
	 * 关键字
	 */
	private String keyWords;

	/**
	 * 技能描述
	 */
	private String description;

	/**
	 * 状态：DRAFT=草稿 / PUBLISHED=已发布 / UNPUBLISHED=未发布
	 */
	private String status;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long createBy;

	private Long updateBy;

}
