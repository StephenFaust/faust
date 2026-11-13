package com.faust.dto.chat.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class ChatHistoryResp {
	private String chatId;
	private Long ucId;
	@Builder.Default
	private List<ChatMessageHistoryResp> chatMessageList = Collections.emptyList();
}
