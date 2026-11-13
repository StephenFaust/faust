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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for ToolResponseMessage.
 * Provides serialization-friendly representation with default constructor and getters/setters.
 */
@Data
public class ToolResponseMessageDTO implements MessageDTO {

	private String messageType = "tool-response";

	private String content;

	private Map<String, Object> metadata;

	private List<ToolResponseDTO> responses;

	/**
	 * Default constructor for deserialization.
	 */
	public ToolResponseMessageDTO() {
		this.metadata = new HashMap<>();
		this.responses = new ArrayList<>();
	}


	/**
	 * Constructor from Spring AI ToolResponseMessage.
	 */
	public ToolResponseMessageDTO(ToolResponseMessage message) {
		this();
		this.content = message.getText();
		this.metadata = new HashMap<>(message.getMetadata());

		// Convert tool responses
		if (!CollectionUtils.isEmpty(message.getResponses())) {
			this.responses = new ArrayList<>();
			for (ToolResponseMessage.ToolResponse response : message.getResponses()) {
				this.responses.add(new ToolResponseDTO(response));
			}
		}
	}

	/**
	 * Convert to Spring AI ToolResponseMessage.
	 */
	public ToolResponseMessage toToolResponseMessage() {
		List<ToolResponseMessage.ToolResponse> springResponses = new ArrayList<>();
		if (this.responses != null) {
			for (ToolResponseDTO dto : this.responses) {
				springResponses.add(dto.toToolResponse());
			}
		}

		return ToolResponseMessage.builder()
				.responses(springResponses)
				.metadata(this.metadata)
				.build();
	}


	/**
	 * DTO for ToolResponse within ToolResponseMessage.
	 */
	@Data
	public static class ToolResponseDTO {

		private String id;


		private String name;


		private String responseData;


		public ToolResponseDTO(ToolResponseMessage.ToolResponse response) {
			this.id = response.id();
			this.name = response.name();
			this.responseData = response.responseData();
		}

		public ToolResponseMessage.ToolResponse toToolResponse() {
			return new ToolResponseMessage.ToolResponse(this.id, this.name, this.responseData);
		}

	}
}

