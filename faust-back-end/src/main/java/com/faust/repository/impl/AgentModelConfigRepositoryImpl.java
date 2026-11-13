package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentModelConfig;
import com.faust.mapper.AgentModelConfigMapper;
import com.faust.repository.AgentModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentModelConfigRepositoryImpl extends ServiceImpl<AgentModelConfigMapper, AgentModelConfig> implements AgentModelConfigRepository {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveOrUpdateConfig(AgentModelConfig config) {
		AgentModelConfig exist = lambdaQuery()
			.eq(AgentModelConfig::getAgentId, config.getAgentId())
			.one();
		if (exist != null) {
			config.setId(exist.getId());
			config.setCreateBy(null);
			updateById(config);
		} else {
			save(config);
		}
	}

	@Override
	public AgentModelConfig getConfigByAgentId(Long agentId) {
		return lambdaQuery()
			.eq(AgentModelConfig::getAgentId, agentId)
			.one();
	}
}
