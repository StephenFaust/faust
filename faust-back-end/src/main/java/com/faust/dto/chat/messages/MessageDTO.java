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

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Base interface for message DTOs with polymorphic serialization support.
 * Uses Jackson annotations for type discrimination during JSON serialization/deserialization.
 */
public interface MessageDTO {

	/**
	 * Get the message type identifier.
	 */
	String getMessageType();

	/**
	 * Get the content of the message.
	 */
	String getContent();

	/**
	 * Factory class for converting between Spring AI Message and DTO types.
	 */
	class MessageDTOFactory {

		/**
		 * Convert Spring AI Message to appropriate DTO type.
		 */
		public static MessageDTO fromMessage(Message message, boolean isFinal) {
			if (message == null) {
				return null;
			}
			if (message instanceof AgentInstructionMessage) {
				return null;
			}
			if (message instanceof AssistantMessage assistantMessage) {
				if (isFinal) {
					return new AssistantMessageDTO(assistantMessage, true);
				}
				if (assistantMessage.hasToolCalls()) {
					return new ToolRequestMessageDTO(assistantMessage);
				} else {
					return new AssistantMessageDTO(assistantMessage, false);
				}
			} else if (message instanceof UserMessage userMessage) {
				return new UserMessageDTO(userMessage);
			} else if (message instanceof ToolResponseMessage toolResponseMessage) {
				return new ToolResponseMessageDTO(toolResponseMessage);
			} else {
				throw new IllegalArgumentException(
					"Unsupported message type: " + message.getClass().getName()
				);
			}
		}

		/**
		 * Convert InterruptionMetadata to ToolRequestMessageDTO.
		 * This is a specialized method for handling interruption metadata from agent execution.
		 */
		public static ToolRequestConfirmMessageDTO fromInterruptionMetadata(InterruptionMetadata interruptionMetadata) {
			if (interruptionMetadata == null) {
				return null;
			}
			return new ToolRequestConfirmMessageDTO(interruptionMetadata);
		}

		/**
		 * Convert DTO to Spring AI Message.
		 */
		public static Message toMessage(MessageDTO dto) {
			if (dto == null) {
				return null;
			}

			if (dto instanceof ToolRequestMessageDTO) {
				return ((ToolRequestMessageDTO) dto).toAssistantMessage();
			} else if (dto instanceof AssistantMessageDTO) {
				return ((AssistantMessageDTO) dto).toAssistantMessage();
			} else if (dto instanceof UserMessageDTO) {
				return ((UserMessageDTO) dto).toUserMessage();
			} else if (dto instanceof ToolResponseMessageDTO) {
				return ((ToolResponseMessageDTO) dto).toToolResponseMessage();
			} else {
				throw new IllegalArgumentException(
					"Unsupported DTO type: " + dto.getClass().getName()
				);
			}
		}
	}
}

