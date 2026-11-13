package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.constant.DbConstants;
import com.faust.entity.AgentSystemPrompt;
import com.faust.mapper.AgentSystemPromptMapper;
import com.faust.repository.AgentSystemPromptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentSystemPromptRepositoryImpl extends ServiceImpl<AgentSystemPromptMapper, AgentSystemPrompt> implements AgentSystemPromptRepository {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updatePrompt(Long agentId, String content) {
		AgentSystemPrompt active = lambdaQuery()
				.eq(AgentSystemPrompt::getAgentId, agentId)
				.one();
		if (active != null) {
			active.setContent(content);
			updateById(active);
			return;
		}
		AgentSystemPrompt newActive = new AgentSystemPrompt();
		newActive.setAgentId(agentId);
		newActive.setContent(content);
		save(newActive);
	}


	@Override
	public AgentSystemPrompt getSystemPrompt(Long agentId) {
		return lambdaQuery()
				.eq(AgentSystemPrompt::getAgentId, agentId)
				.last(DbConstants.LIMIT_ONE)
				.one();
	}


	@Override
	public List<AgentSystemPrompt> listPromptHistory(Long agentId) {
		return lambdaQuery()
				.eq(AgentSystemPrompt::getAgentId, agentId)
				.list();
	}


}
