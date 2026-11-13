package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentSkill;
import com.faust.entity.AgentSkillItem;

import java.util.Collection;
import java.util.List;

public interface AgentSkillRepository extends IService<AgentSkill> {

	/**
	 * 保存技能及其条目
	 */
	Long saveSkillWithItems(AgentSkill skill, List<AgentSkillItem> items, Long ucId);

	/**
	 * 更新技能及其条目（条目全量替换）
	 */
	void updateSkill(AgentSkill skill, List<AgentSkillItem> items, Long ucId);

	/**
	 * 删除技能及其条目
	 */
	void deleteSkill(Long skillId);

	/**
	 * 按 ID 批量查询已发布的技能
	 */
	List<AgentSkill> listPublishedByIds(Collection<Long> skillIds);
}
