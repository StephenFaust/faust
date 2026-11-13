package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentToolRel;

import java.util.List;

public interface AgentToolRelRepository extends IService<AgentToolRel> {

	void bindToolToAgent(Long agentId, Long toolId, Long ucId);

	void unbindTool(Long agentId, Long toolId);

	void unbindAllTool(Long agentId);

	List<Long> listToolIdsByAgentId(Long agentId);


	List<AgentToolRel> listByToolId(Long toolId);
}
