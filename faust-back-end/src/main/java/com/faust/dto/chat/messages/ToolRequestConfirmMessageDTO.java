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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for AssistantMessage.
 * Provides serialization-friendly representation with default constructor and getters/setters.
 */
@Data
public class ToolRequestConfirmMessageDTO implements MessageDTO {


	private String messageType = "tool-confirm";


	private String content;


	private Map<String, Object> metadata;


	private List<ToolFeedback> toolFeedback;


	private List<ToolCallDTO> toolsAutomaticallyApproved;

	/**
	 * Default constructor for deserialization.
	 */
	public ToolRequestConfirmMessageDTO() {
		this.metadata = new HashMap<>();
		this.toolFeedback = new ArrayList<>();
		this.toolsAutomaticallyApproved = new ArrayList<>();
	}


	/**
	 * Constructor from InterruptionMetadata.
	 */
	public ToolRequestConfirmMessageDTO(InterruptionMetadata interruptionMetadata) {
		this();
		this.content = "";
		this.metadata = new HashMap<>(interruptionMetadata.metadata().orElse(Map.of()));

		// Convert InterruptionMetadata.ToolFeedback to ToolFeedback
		if (interruptionMetadata.toolFeedbacks() != null && !interruptionMetadata.toolFeedbacks().isEmpty()) {
			this.toolFeedback = new ArrayList<>();
			for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
				ToolFeedback toolFeedback = new ToolFeedback();
				toolFeedback.setId(feedback.getId());
				toolFeedback.setName(feedback.getName());
				toolFeedback.setArguments(feedback.getArguments());
				toolFeedback.setDescription(feedback.getDescription());

				// Convert FeedbackResult
				if (feedback.getResult() != null) {
					toolFeedback.setResult(ToolFeedback.FeedbackResult.valueOf(feedback.getResult().name()));
				}

				this.toolFeedback.add(toolFeedback);
			}
		}

		// Convert toolsAutomaticallyApproved
		if (interruptionMetadata.getToolsAutomaticallyApproved() != null
				&& !interruptionMetadata.getToolsAutomaticallyApproved().isEmpty()) {
			this.toolsAutomaticallyApproved = new ArrayList<>();
			for (AssistantMessage.ToolCall toolCall : interruptionMetadata.getToolsAutomaticallyApproved()) {
				this.toolsAutomaticallyApproved.add(new ToolCallDTO(toolCall));
			}
		}
	}

	/**
	 * Convert to InterruptionMetadata.
	 *
	 * @param nodeId the node ID where the interruption occurred
	 * @param state  the overall state at the time of interruption
	 */
	public InterruptionMetadata toInterruptionMetadata(String nodeId, OverAllState state) {
		InterruptionMetadata.Builder builder = InterruptionMetadata.builder(nodeId, state);
		// Set metadata
		if (this.metadata != null) {
			for (Map.Entry<String, Object> entry : this.metadata.entrySet()) {
				builder.addMetadata(entry.getKey(), entry.getValue());
			}
		}

		// Convert ToolFeedback to InterruptionMetadata.ToolFeedback
		if (this.toolFeedback != null && !this.toolFeedback.isEmpty()) {
			for (ToolFeedback feedback : this.toolFeedback) {
				InterruptionMetadata.ToolFeedback.FeedbackResult result =
						feedback.getResult() != null
								? InterruptionMetadata.ToolFeedback.FeedbackResult.valueOf(feedback.getResult().name())
								: InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED;

				InterruptionMetadata.ToolFeedback interruptionToolFeedback =
						new InterruptionMetadata.ToolFeedback(
								feedback.getId(),
								feedback.getName(),
								feedback.getArguments(),
								result,
								feedback.getDescription()
						);

				builder.addToolFeedback(interruptionToolFeedback);
			}
		}

		// Convert toolsAutomaticallyApproved
		if (this.toolsAutomaticallyApproved != null && !this.toolsAutomaticallyApproved.isEmpty()) {
			List<AssistantMessage.ToolCall> springToolCalls = new ArrayList<>();
			for (ToolCallDTO toolCallDTO : this.toolsAutomaticallyApproved) {
				springToolCalls.add(toolCallDTO.toToolCall());
			}
			builder.toolsAutomaticallyApproved(springToolCalls);
		}

		return builder.build();
	}

	/**
	 * DTO for ToolFeedback.
	 */
	@Data
	public static class ToolFeedback {

		private String id;


		private String name;


		private String arguments;


		private FeedbackResult result;


		private String description;


		// Getters and Setters


		/**
		 * Enum for ToolFeedback result.
		 */
		public enum FeedbackResult {
			APPROVED,
			REJECTED,
			EDITED;
		}
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

