package com.faust.agent.executor;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.faust.constant.AgentConstants;
import com.faust.agent.factory.AgentFactory;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.dto.chat.messages.MessageDTO;
import com.faust.dto.chat.messages.ToolRequestConfirmMessageDTO;
import com.faust.dto.chat.resp.AgentRunResp;
import com.faust.dto.chat.resp.ChatResp;
import com.faust.enums.ErrorMessageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

	private final AgentFactory agentFactory;

	private ReactAgent getAgent(String agentCode, String version) {
		ReactAgent agent = agentFactory.getAgent(agentCode, version);
		ThrowExceptionFunction.isNull(agent)
			.throwMessage(ErrorMessageEnum.AGENT_IS_NULL);
		return agent;
	}

	/**
	 * 开始对话
	 * <p>
	 * Skip AGENT_MODEL_FINISHED: it is the framework's aggregation of AGENT_MODEL_STREAMING
	 * chunks; sending it to the frontend would duplicate the final message.
	 * First frame: hand the chatId back to the frontend so a brand-new session can
	 * persist its record, load history, and continue multi-turn conversation.
	 *
	 * @param agentCode agent编码
	 * @param version   版本
	 * @param chatId    会话id
	 * @param userMsg   用户内容
	 * @param ucId      用户id
	 * @return resp
	 */
	public Flux<ServerSentEvent<String>> doChatStream(String agentCode, String version,
													  String chatId, UserMessage userMsg,
													  Long ucId, Map<String, String> envVar) {
		ServerSentEvent<String> chatIdFrame = ServerSentEvent.<String>builder()
			.data("{\"chatId\":\"" + chatId + "\"}")
			.build();
		MDC.put(AgentConstants.CHAT_ID, chatId);
		try {
			check(chatId, userMsg);
			ReactAgent reactAgent = getAgent(agentCode, version);
			RunnableConfig config = RunnableConfig.builder()
				.addMetadata(AgentConstants.UCID, ucId.toString())
				.addMetadata(AgentConstants.ENV_VAR, envVar)
				.threadId(chatId).build();
			LOGGER.info("start invoke the agent steam chat, chatId:{}, userMsg:{}", chatId, userMsg);
			Flux<NodeOutput> agentStream = reactAgent.stream(userMsg, config);
			Flux<ServerSentEvent<String>> agentSseStream = agentStream
				.map(nodeOutput -> {
					String node = nodeOutput.node();
					String agentName = nodeOutput.agent();
					Usage tokenUsage = nodeOutput.tokenUsage();
					AgentRunResp agentResponse = null;
					if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
						Message message = streamingOutput.message();
						if (message == null) {
							// no update, typically output responses from nodes that does not produce messages
							return ServerSentEvent.<String>builder()
								.data("{}")
								.build();
						}
						if (message instanceof AssistantMessage assistantMessage) {
							if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
								agentResponse = new AgentRunResp(node, agentName, assistantMessage, tokenUsage, assistantMessage.getText(), true);
							} else {
								if (assistantMessage.hasToolCalls()) {
									agentResponse = new AgentRunResp(node, agentName, assistantMessage,
										tokenUsage, "", false);
								} else {
									agentResponse = new AgentRunResp(node, agentName, assistantMessage, tokenUsage, assistantMessage.getText(), false);
								}
							}
						} else {
							agentResponse = new AgentRunResp(node, agentName, message, tokenUsage, "", false);
						}
					} else if (nodeOutput instanceof InterruptionMetadata interruptionMetadata) {
						// Use the specialized method to convert InterruptionMetadata to ToolRequestMessageDTO
						ToolRequestConfirmMessageDTO toolRequestMessage = MessageDTO.MessageDTOFactory.fromInterruptionMetadata(interruptionMetadata);
						agentResponse = new AgentRunResp(node, agentName, toolRequestMessage, tokenUsage, "");
					}
					// Serialize to JSON string
					try {
						if (agentResponse != null) {
							String jsonData = JSON.toJSONString(agentResponse);
							return ServerSentEvent.<String>builder()
								.data(jsonData)
								.build();
						}
					} catch (Exception e) {
						LOGGER.error("Failed to serialize AgentRunResponse to JSON", e);
						return ServerSentEvent.<String>builder()
							.data(ErrorMessageBuilder.errorMsg("Failed to serialize response"))
							.build();
					}
					return ServerSentEvent.<String>builder()
						.data("{}")
						.build();
				})
				.onErrorResume(error -> {
					// Handle errors from the agent stream and convert to SSE error event
					LOGGER.error("Error occurred during agent stream execution", error);
					String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error occurred";
					String errorType = error.getClass().getSimpleName();
					// Return the error as an SSE event and complete the stream
					return Flux.just(
						ServerSentEvent.<String>builder()
							.event("error")
							.data(ErrorMessageBuilder.errorMsg(errorMessage, errorType))
							.build()
					);
				});
			return Flux.concat(Flux.just(chatIdFrame), agentSseStream);
		} catch (Exception e) {
			LOGGER.error("Error during agent run for session {}", chatId, e);
			return Flux.concat(Flux.just(chatIdFrame), Flux.just(
				ServerSentEvent.<String>builder()
					.event("error")
					.data(ErrorMessageBuilder.errorMsg())
					.build()
			));
		} finally {
			MDC.remove(AgentConstants.CHAT_ID);
		}
	}


	public ChatResp doChat(String agentCode, String version,
						   String chatId, UserMessage userMsg,
						   Long ucId, Map<String, String> envVar) {
		try {
			check(chatId, userMsg);
			ReactAgent reactAgent = getAgent(agentCode, version);
			RunnableConfig config = RunnableConfig.builder()
				.addMetadata(AgentConstants.UCID, ucId.toString())
				.addMetadata(AgentConstants.ENV_VAR, envVar)
				.threadId(chatId).build();
			LOGGER.info("start invoke the agent  chat, chatId:{}, userMsg:{}", chatId, userMsg);
			AssistantMessage resp = reactAgent.call(userMsg, config);
			return ChatResp.builder().chatId(chatId).agentResp(resp.getText()).build();
		} catch (Exception e) {
			LOGGER.error("invoke the agent failed, chatId:{}, errMsg:{}", chatId, e.getMessage(), e);
			return ChatResp.builder().errorMsg(e.getMessage()).build();
		}
	}


	private void check(String chatId, UserMessage userMsg) {
		ThrowExceptionFunction.isNull(userMsg)
			.throwMessage(ErrorMessageEnum.USER_MESSAGE_NOT_BLANK);
		ThrowExceptionFunction.isBlank(chatId)
			.throwMessage(ErrorMessageEnum.SESSION_NOT_BLANK);
	}


}
