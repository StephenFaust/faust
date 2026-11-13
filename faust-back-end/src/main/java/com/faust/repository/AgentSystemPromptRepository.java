package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentSystemPrompt;

import java.util.List;

public interface AgentSystemPromptRepository extends IService<AgentSystemPrompt> {

	void updatePrompt(Long agentId, String content);


	AgentSystemPrompt getSystemPrompt(Long agentId);

	List<AgentSystemPrompt> listPromptHistory(Long agentId);

}
