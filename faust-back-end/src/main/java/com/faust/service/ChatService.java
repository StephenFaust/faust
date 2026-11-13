package com.faust.service;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.constant.AgentConstants;
import com.faust.entity.AgentConversationRecord;
import com.faust.entity.AgentInfo;
import com.faust.entity.MessageRecordItem;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.enums.ErrorMessageEnum;
import com.faust.support.SpaceGuard;
import com.faust.repository.AgentConversationRecordRepository;
import com.faust.repository.AgentInfoRepository;
import com.faust.repository.MessageRecordItemRepository;
import com.faust.agent.executor.AgentExecutor;
import com.faust.dto.chat.req.ChatHistoryQueryReq;
import com.faust.dto.chat.req.ChatListQueryReq;
import com.faust.dto.chat.req.ChatReq;
import com.faust.dto.chat.resp.ChatHistoryResp;
import com.faust.dto.chat.resp.ChatListResp;
import com.faust.dto.chat.resp.ChatMessageHistoryResp;
import com.faust.dto.chat.resp.ChatResp;
import com.faust.dto.file.FileInfoDTO;
import com.faust.dto.file.resp.FileInfoResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

	private final AgentExecutor agentExecutor;
	private final AgentInfoRepository agentInfoService;
	private final AgentConversationRecordRepository conversationRecordService;
	private final MessageRecordItemRepository messageRecordItemService;
	private final TransactionTemplate transactionTemplate;

	private final GlobalEnvConfigService globalEnvConfigService;

	private final SpaceGuard spaceGuard;

	/**
	 * 开始会话
	 */
	public ChatResp chat(ChatReq req) {
		assertAgentAccessible(req.getAgentCode());
		updateOrSaveConversation(req);
		return agentExecutor.doChat(req.getAgentCode(), req.getVersion(),
				req.getChatId(), buildUserMessage(req), req.getUcId(), req.getEnvVar());
	}


	public Flux<ServerSentEvent<String>> chatStream(ChatReq req) {
		// 空间鉴权必须在同步段完成，Flux 内部拿不到 ThreadLocal 上下文
		assertAgentAccessible(req.getAgentCode());
		updateOrSaveConversation(req);
		return agentExecutor.doChatStream(req.getAgentCode(), req.getVersion(), req.getChatId(),
				buildUserMessage(req), req.getUcId(), req.getEnvVar());
	}

	/**
	 * 对话鉴权：Agent 所属空间的成员才能对话
	 */
	private void assertAgentAccessible(String agentCode) {
		AgentInfo agent = agentInfoService.getAgentByCode(agentCode);
		ThrowExceptionFunction.isNull(agent).throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
		spaceGuard.assertMember(agent.getWorkspaceId());
	}


	private UserMessage buildUserMessage(ChatReq req) {
		UserMessage.Builder builder = UserMessage.builder()
				.text(req.getUserMsg());// 文本内容
		if (CollectionUtils.isNotEmpty(req.getFileInfoList())) {
			List<FileInfoDTO> fileInfoList = req.getFileInfoList().stream().map(e -> new FileInfoDTO(e.getFileName(), e.getFileType(), e.getUrl())).toList();
			builder.metadata(Map.of(AgentConstants.FILE_INFO_KEY, fileInfoList));
		}
		return builder.build();
	}

	/**
	 * 会话列表：根据 ucId + agentCode 查询）
	 */
	public Page<ChatListResp> chatList(ChatListQueryReq query) {
		Page<AgentConversationRecord> page = conversationRecordService
				.pageByUcIdAndAgentCode(query.getUcId(), query.getAgentCode(), query.getCurrent(), query.getPageSize());
		Page<ChatListResp> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
		result.setRecords(page.getRecords().stream()
				.map(record -> {
					ChatListResp resp = new ChatListResp();
					resp.setChatId(record.getChatId());
					resp.setTitle(record.getTitle());
					resp.setChatTime(LocalDateTimeUtil.formatNormal(record.getCreateTime()));
					return resp;
				}).toList());
		return result;
	}


	public Map<String, String> getChatEnvVar(String chatId, Long ucId) {
		return conversationRecordService.getChatEnvVar(chatId, ucId);
	}


	/**
	 * 获取历史消息
	 *
	 * @param req 请求
	 * @return 聊天历史
	 */
	public ChatHistoryResp getChatHistory(ChatHistoryQueryReq req) {
		ChatHistoryResp.ChatHistoryRespBuilder builder = ChatHistoryResp.builder();
		AgentConversationRecord record = conversationRecordService.getByChatId(req.getChatId(), req.getUcId());
		// 会话不存在或不属于当前登录用户，直接返回空
		if (Objects.isNull(record)) {
			return builder.build();
		}
		builder.ucId(record.getUcId()).chatId(record.getChatId());
		// 分页拉取会话消息：第 1 页为最近的一批，向上翻阅时 current 递增
		List<MessageRecordItem> messageRecordItems = messageRecordItemService
				.pageHistory(req.getChatId(), req.getPageSize(), req.getLastMessageId());
		List<ChatMessageHistoryResp> messageHistoryList = CollectionUtils.isEmpty(messageRecordItems)
				? Collections.emptyList()
				: messageRecordItems.stream().map(this::toMessageResp).toList();
		builder.chatMessageList(messageHistoryList);
		return builder.build();
	}


	private ChatMessageHistoryResp toMessageResp(MessageRecordItem item) {
		List<FileInfoDTO> fileList = item.getFileList();
		if (CollectionUtils.isEmpty(fileList)) {
			return ChatMessageHistoryResp.builder()
					.role(item.getType())
					.id(item.getId())
					.message(item.getContent())
					.build();
		}
		List<FileInfoResp> fileInfoRespList = fileList.stream()
				.map(file -> FileInfoResp.builder()
						.fileName(file.getFileName())
						.fileType(file.getFileType())
						.url(file.getUrl()).build()).toList();
		return ChatMessageHistoryResp.builder()
				.role(item.getType())
				.id(item.getId())
				.message(item.getContent())
				.fileInfoRespList(fileInfoRespList)
				.build();
	}


	private void updateOrSaveConversation(ChatReq req) {
		transactionTemplate.executeWithoutResult(status -> {
			// 新会话自动生成 chatId 并保存记录
			if (StringUtils.isBlank(req.getChatId())) {
				req.setChatId(UUID.randomUUID().toString());
			}
			AgentConversationRecord chatRecord = conversationRecordService.getByChatId(req.getChatId(), req.getUcId());
			if (Objects.isNull(chatRecord)) {
				// 新会话：调用方未表态（envVar == null）时继承「该 Agent × 当前用户」的全局环境变量。
				// 继承后仍然落在 conversation_record.env_var，成为该会话的“临时变量”：
				// 后续会话内修改只影响本会话，不会再回写全局配置。
				// 显式传空 Map 表示用户主动清空，不再继承。
				if (req.getEnvVar() == null) {
					req.setEnvVar(globalEnvConfigService.getEnvVar(req.getAgentCode(), req.getUcId()));
				}
				conversationRecordService.saveChatRecord(req);
				agentInfoService.increaseUseNum(req.getAgentCode(), req.getVersion());
			} else {
				// 已有会话：沿用请求带来的环境变量（前端从会话记录回显，可在会话内修改）
				conversationRecordService.updateConversation(req);
			}
			// 下游 metadata 需要非 null 的 Map（调用方未传时兜底为空集合）
			if (req.getEnvVar() == null) {
				req.setEnvVar(new HashMap<>(0));
			}
		});
	}


}
