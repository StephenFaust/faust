package com.faust.agent.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.faust.constant.AgentConstants;
import com.faust.repository.MessageRecordItemRepository;
import com.faust.dto.file.FileInfoDTO;
import com.faust.enums.MessageRecordType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class MessageSaveAndHandleHook extends AgentHook {


	private final MessageRecordItemRepository messageRecordItemService;

	private static final String FILE_MESSAGE_TEMPLATE = "\n File list: ";
	private static final String FILE_MESSAGE_ITEM_TEMPLATE = "\n fileName: %s, fileType: %s, fileUrl: %s ;";

	@Override
	public String getName() {
		return "message_save_and_handle_hookV2";
	}


	@Override
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		List<Message> messages = getMessages(state);
		Message latestMessage = getLatestMessage(messages);
		if (Objects.isNull(latestMessage)) {
			return CompletableFuture.completedFuture(Map.of());
		}
		Optional<String> chatIdOpt = config.threadId();
		if (chatIdOpt.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}
		String chatId = chatIdOpt.get();
		if (latestMessage instanceof UserMessage userMessage) {
			Map<String, Object> metadata = userMessage.getMetadata();
			if (MapUtils.isEmpty(metadata)) {
				messageRecordItemService.add(userMessage.getText(), MessageRecordType.USER, chatId, null);
			} else {
				Object fileInfoObj = metadata.get(AgentConstants.FILE_INFO_KEY);
				if (Objects.isNull(fileInfoObj)) {
					messageRecordItemService.add(userMessage.getText(), MessageRecordType.USER, chatId, null);
				} else {
					StringBuilder text = new StringBuilder(userMessage.getText());
					@SuppressWarnings("unchecked")
					List<FileInfoDTO> fileInfoList = (List<FileInfoDTO>) fileInfoObj;
					messageRecordItemService.add(text.toString(), MessageRecordType.USER, chatId, fileInfoList);
					text.append(FILE_MESSAGE_TEMPLATE);
					for (FileInfoDTO fileInfoDTO : fileInfoList) {
						text.append(String.format(FILE_MESSAGE_ITEM_TEMPLATE, fileInfoDTO.getFileName(),
								fileInfoDTO.getFileType(), fileInfoDTO.getUrl()));
					}
					userMessage.getMetadata().remove(AgentConstants.FILE_INFO_KEY);
					messages.remove(messages.size() - 1);
					UserMessage newUserMessage = UserMessage.builder()
							.text(text.toString())
							.media(userMessage.getMedia())
							.metadata(userMessage.getMetadata())
							.build();
					messages.add(newUserMessage);
				}
			}
		}
		return CompletableFuture.completedFuture(Map.of());
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
		List<Message> messages = getMessages(state);
		Optional<String> chatIdOpt = config.threadId();
		if (chatIdOpt.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}
		String chatId = chatIdOpt.get();
		Deque<String> stack = new ArrayDeque<>(messages.size());
		for (int i = messages.size() - 1; i >= 0; i--) {
			Message msg = messages.get(i);
			if (msg instanceof UserMessage) {
				break;
			}
			if (msg instanceof AssistantMessage am) {
				stack.push(am.getText()); // 压栈，先进后出保证顺序
			}
		}
		StringBuilder llmMsgRecordSb = new StringBuilder();
		while (!stack.isEmpty()) {
			llmMsgRecordSb.append(stack.pop());
		}
		String llmMsgRecord = llmMsgRecordSb.toString();
		if (StringUtils.isNotBlank(llmMsgRecord)) {
			messageRecordItemService.add(llmMsgRecordSb.toString(), MessageRecordType.LLM, chatId, null);
		}
		return CompletableFuture.completedFuture(Map.of());
	}


	@SuppressWarnings("unchecked")
	private List<Message> getMessages(OverAllState state) {
		return (List<Message>) state.data().getOrDefault("messages", List.of());
	}

	private Message getLatestMessage(List<Message> previousMessages) {
		if (CollectionUtils.isEmpty(previousMessages)) {
			return null;
		}
		return previousMessages.get(previousMessages.size() - 1);
	}
}
