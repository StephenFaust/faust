package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent与技能关联表（多对多关系）
 */
@Data
@TableName("agent_skill_rel")
public class AgentSkillRel {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 关联 agent_info.id
	 */
	private Long agentId;

	/**
	 * 关联 agent_skill.id
	 */
	private Long skillId;

	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;

}
