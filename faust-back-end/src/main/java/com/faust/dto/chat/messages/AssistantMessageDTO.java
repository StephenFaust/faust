/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.faust.dto.chat.messages;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for AssistantMessage.
 * Provides serialization-friendly representation with default constructor and getters/setters.
 */
@Data
public class AssistantMessageDTO implements MessageDTO {


	private String messageType = "assistant";


	private String content;

	private Map<String, Object> metadata;


	private List<ToolCallDTO> toolCalls;

	/**
	 * Default constructor for deserialization.
	 */
	public AssistantMessageDTO() {
		this.metadata = new HashMap<>();
		this.toolCalls = new ArrayList<>();
	}

	/**
	 * Constructor from Spring AI AssistantMessage.
	 */
	public AssistantMessageDTO(AssistantMessage message, boolean isFinal) {
		this();
		if (isFinal) {
			messageType = "final-assistant";
		}
		this.content = message.getText();
		this.metadata = new HashMap<>(message.getMetadata());

		// Convert tool calls if present
		if (!CollectionUtils.isEmpty(message.getToolCalls())) {
			this.toolCalls = new ArrayList<>();
			for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
				this.toolCalls.add(new ToolCallDTO(toolCall));
			}
		}
	}


	/**
	 * Convert to Spring AI AssistantMessage.
	 */
	public AssistantMessage toAssistantMessage() {
		List<AssistantMessage.ToolCall> springToolCalls = new ArrayList<>();
		if (this.toolCalls != null) {
			for (ToolCallDTO dto : this.toolCalls) {
				springToolCalls.add(dto.toToolCall());
			}
		}

		return AssistantMessage.builder()
			.content(this.content)
			.properties(this.metadata)
			.toolCalls(springToolCalls)
			.build();
	}


	/**
	 * DTO for ToolCall within AssistantMessage.
	 */
	@Data
	public static class ToolCallDTO {

		private String id;


		private String type;


		private String name;


		private String arguments;


		public ToolCallDTO(AssistantMessage.ToolCall toolCall) {
			this.id = toolCall.id();
			this.type = toolCall.type();
			this.name = toolCall.name();
			this.arguments = toolCall.arguments();
		}

		public AssistantMessage.ToolCall toToolCall() {
			return new AssistantMessage.ToolCall(this.id, this.type, this.name, this.arguments);
		}


	}
}

