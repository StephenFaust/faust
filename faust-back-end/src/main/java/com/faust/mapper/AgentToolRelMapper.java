package com.faust.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.entity.AgentToolRel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentToolRelMapper extends BaseMapper<AgentToolRel> {

	/**
	 * 原子绑定：只有「工具已发布 + 与 Agent 同空间（或系统空间工具）」时才会插入。
	 *
	 * <p>把 check-then-insert 收敛成一条 INSERT ... SELECT：
	 * 即使分布式锁失效，也不会出现「工具已下架却仍被绑定」的脏数据（T1 / T2 / I6）。</p>
	 *
	 * @return 1 = 绑定成功；0 = 工具不存在 / 未发布 / 跨空间，需要调用方按原因报错
	 */
	@Insert("""
		INSERT INTO agent_tool_rel (agent_id, tool_id)
		SELECT #{agentId}, t.id
		FROM agent_tool t
		JOIN agent_info a ON a.id = #{agentId}
		WHERE t.id = #{toolId}
		  AND t.status = 'PUBLISHED'
		  AND (t.workspace_id = a.workspace_id OR t.workspace_id = 0)
		""")
	int bindIfPublished(@Param("agentId") Long agentId, @Param("toolId") Long toolId);
}
