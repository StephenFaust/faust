package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentConversationRecord;
import com.faust.mapper.AgentConversationRecordMapper;
import com.faust.repository.AgentConversationRecordRepository;
import com.faust.dto.chat.req.ChatReq;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AgentConversationRecordRepositoryImpl extends ServiceImpl<AgentConversationRecordMapper, AgentConversationRecord>
	implements AgentConversationRecordRepository {


	@Override
	public void saveChatRecord(ChatReq req) {
		AgentConversationRecord record = AgentConversationRecord.builder()
			.ucId(req.getUcId())
			.agentCode(req.getAgentCode())
			.version(req.getVersion())
			.chatId(req.getChatId())
			.envVar(req.getEnvVar())
			.title(generateTitle(req.getUserMsg())).build();
		save(record);
	}

	@Override
	public Page<AgentConversationRecord> pageByUcIdAndAgentCode(Long ucId, String agentCode, Integer current, Integer size) {
		Page<AgentConversationRecord> page = new Page<>();
		page.setCurrent(current);
		page.setSize(size);
		LambdaQueryWrapper<AgentConversationRecord> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentConversationRecord::getUcId, ucId)
			.eq(AgentConversationRecord::getAgentCode, agentCode)
			.orderByDesc(AgentConversationRecord::getId);
		return page(page, wrapper);
	}

	@Override
	public AgentConversationRecord getByChatId(String chatId, Long ucId) {
		LambdaQueryWrapper<AgentConversationRecord> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentConversationRecord::getChatId, chatId);
		wrapper.eq(AgentConversationRecord::getUcId, ucId);
		return getOne(wrapper);
	}


	@Override
	public void updateConversation(ChatReq req) {
		LambdaUpdateWrapper<AgentConversationRecord> updateWrapper = Wrappers.lambdaUpdate();
		updateWrapper.eq(AgentConversationRecord::getChatId, req.getChatId())
			.set(AgentConversationRecord::getUpdateTime, LocalDateTime.now());
		// 未传（null）表示不覆盖会话已有变量；传空 Map 表示显式清空
		if (req.getEnvVar() != null) {
			updateWrapper.set(AgentConversationRecord::getEnvVar, req.getEnvVar(),
				"javaType=java.util.Map," +
					"jdbcType=VARCHAR," +
					"typeHandler=com.faust.common.typehandler.JsonMapTypeHandler");
		}
		update(updateWrapper);
	}

	@Override
	public Map<String, String> getChatEnvVar(String chatId, Long ucId) {
		LambdaQueryWrapper<AgentConversationRecord> wrapper = Wrappers.lambdaQuery();
		wrapper.select(AgentConversationRecord::getEnvVar);
		wrapper.eq(AgentConversationRecord::getChatId, chatId);
		wrapper.eq(AgentConversationRecord::getUcId, ucId);
		AgentConversationRecord record = getOne(wrapper);
		if (Objects.isNull(record) || MapUtils.isEmpty(record.getEnvVar())) {
			return new HashMap<>(0);
		}
		return record.getEnvVar();
	}

	private String generateTitle(String userMsg) {
		if (StringUtils.isBlank(userMsg)) {
			return "未知";
		}
		return StringUtils.abbreviate(userMsg, 50);
	}

}
