package com.faust.dto.chat.resp;

import com.faust.dto.chat.messages.MessageDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

@Data
@NoArgsConstructor
public class AgentRunResp {


	private String node;

	private String agent;


	private Usage tokenUsage;

	/**
	 * The last message in DTO format for better serialization.
	 */

	private MessageDTO message;


	private String chunk;


	public AgentRunResp(String node, String agent, Message message, Usage tokenUsage, String chunk, boolean isFinal) {
		this.node = node;
		this.agent = agent;
		this.message = message != null ? MessageDTO.MessageDTOFactory.fromMessage(message, isFinal) : null;
		this.tokenUsage = tokenUsage;
		this.chunk = chunk;
	}

	/**
	 * Constructor for creating response with MessageDTO directly.
	 */
	public AgentRunResp(String node, String agent, MessageDTO message, Usage tokenUsage, String chunk) {
		this.node = node;
		this.agent = agent;
		this.message = message;
		this.tokenUsage = tokenUsage;
		this.chunk = chunk;
	}
}
