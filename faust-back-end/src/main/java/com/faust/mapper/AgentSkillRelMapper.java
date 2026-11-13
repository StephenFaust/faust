package com.faust.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.entity.AgentSkillRel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentSkillRelMapper extends BaseMapper<AgentSkillRel> {

	/**
	 * 原子绑定：只有「技能已发布 + 与 Agent 同空间」时才会插入。
	 *
	 * <p>与 {@code AgentToolRelMapper#bindIfPublished} 同理，用于消除
	 * 「检查时已发布 → 插入时已下架」的窗口（S1 / S2 / I6）。</p>
	 *
	 * @return 1 = 绑定成功；0 = 技能不存在 / 未发布 / 跨空间，需要调用方按原因报错
	 */
	@Insert("""
		INSERT INTO agent_skill_rel (agent_id, skill_id)
		SELECT #{agentId}, s.id
		FROM agent_skill s
		JOIN agent_info a ON a.id = #{agentId}
		WHERE s.id = #{skillId}
		  AND s.status = 'PUBLISHED'
		  AND s.workspace_id = a.workspace_id
		""")
	int bindIfPublished(@Param("agentId") Long agentId, @Param("skillId") Long skillId);
}
