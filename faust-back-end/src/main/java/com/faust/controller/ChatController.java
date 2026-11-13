package com.faust.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.dto.chat.req.ChatHistoryQueryReq;
import com.faust.dto.chat.req.ChatListQueryReq;
import com.faust.dto.chat.req.ChatReq;
import com.faust.dto.chat.resp.ChatHistoryResp;
import com.faust.dto.chat.resp.ChatListResp;
import com.faust.dto.chat.resp.ChatResp;
import com.faust.common.api.ApiResponse;
import com.faust.service.ChatService;
import com.faust.common.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/chat")
@Tag(name = "对话管理", description = "新建会话、继续会话、会话列表查询")
public class ChatController {

	private final ChatService chatService;

	@PostMapping("/start")
	@Operation(summary = "发起对话", description = "开始与 Agent 对话")
	public ApiResponse<ChatResp> startChat(@RequestBody @Valid ChatReq request) {
		request.setUcId(UserContext.getUcId());
		return ApiResponse.success(chatService.chat(request));
	}

	@PostMapping(path = "/start/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "发起对话", description = "开始与 Agent 对话")
	public Flux<ServerSentEvent<String>> startChatSSE(@RequestBody @Valid ChatReq request) {
		request.setUcId(UserContext.getUcId());
		return chatService.chatStream(request);
	}


	@GetMapping("/list")
	@Operation(summary = "会话列表", description = "根据 ucId 和 agentId 查询会话列表")
	public ApiResponse<Page<ChatListResp>> chatList(@Valid ChatListQueryReq query) {
		query.setUcId(UserContext.getUcId());
		return ApiResponse.success(chatService.chatList(query));
	}


	@GetMapping("/env/var")
	@Operation(summary = "环境变量", description = "根据 ucId 和 chatId 查询环境变量记录")
	public ApiResponse<Map<String, String>> chatEnvVar(@RequestParam(name = "chatId") String chatId) {
		Long ucId = UserContext.getUcId();
		return ApiResponse.success(chatService.getChatEnvVar(chatId, ucId));
	}

	@GetMapping("/history")
	@Operation(summary = "会话历史", description = "根据 chatId 查询会话历史")
	public ApiResponse<ChatHistoryResp> getChatHistory(@Valid ChatHistoryQueryReq query) {
		query.setUcId(UserContext.getUcId());
		return ApiResponse.success(chatService.getChatHistory(query));
	}


}
