package com.faust.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentConversationRecord;
import com.faust.dto.chat.req.ChatReq;

import java.util.Map;

public interface AgentConversationRecordRepository extends IService<AgentConversationRecord> {


	/**
	 * 存入会话记录
	 *
	 * @param req 入参
	 */
	void saveChatRecord(ChatReq req);


	/**
	 * 根据 ucId + agentCode 查询会话记录，按更新时间倒序
	 */
	Page<AgentConversationRecord> pageByUcIdAndAgentCode(Long ucId, String agentCode, Integer current, Integer size);

	/**
	 * 根据 chatId 查询会话记录
	 */
	AgentConversationRecord getByChatId(String chatId, Long ucId);


	/**
	 * 更新会活跃时间
	 *
	 * @param req 入参
	 */
	void updateConversation(ChatReq req);

	/**
	 * 获取环境变量
	 *
	 * @param chatId 会话id
	 */
	Map<String, String> getChatEnvVar(String chatId, Long ucId);
}
