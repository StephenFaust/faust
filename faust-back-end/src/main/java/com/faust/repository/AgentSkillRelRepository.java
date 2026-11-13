package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentSkillRel;

import java.util.List;

public interface AgentSkillRelRepository extends IService<AgentSkillRel> {

	/**
	 * 绑定技能到 Agent（仅允许绑定已发布技能）
	 */
	void bindSkillToAgent(Long agentId, Long skillId, Long ucId);

	/**
	 * 解除 Agent 与指定技能的绑定
	 */
	void unbindSkill(Long agentId, Long skillId);

	/**
	 * 解除 Agent 的全部技能绑定
	 */
	void unbindAllSkill(Long agentId);

	/**
	 * 查询 Agent 绑定的技能 ID 列表
	 */
	List<Long> listSkillIdsByAgentId(Long agentId);

	/**
	 * 查询绑定了指定技能的关联记录
	 */
	List<AgentSkillRel> listBySkillId(Long skillId);
}
