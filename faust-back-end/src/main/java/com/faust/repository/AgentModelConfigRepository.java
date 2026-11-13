package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentModelConfig;

public interface AgentModelConfigRepository extends IService<AgentModelConfig> {

	void saveOrUpdateConfig(AgentModelConfig config);

	AgentModelConfig getConfigByAgentId(Long agentId);
}
