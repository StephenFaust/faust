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
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for UserMessage.
 * Provides serialization-friendly representation with default constructor and getters/setters.
 */
@Data
public class UserMessageDTO implements MessageDTO {


	private String messageType = "user";


	private String content;


	private Map<String, Object> metadata;


	private List<MediaDTO> media;

	/**
	 * Default constructor for deserialization.
	 */
	public UserMessageDTO() {
		this.metadata = new HashMap<>();
		this.media = new ArrayList<>();
	}



	/**
	 * Constructor from Spring AI UserMessage.
	 */
	public UserMessageDTO(UserMessage message) {
		this();
		this.content = message.getText();
		this.metadata = new HashMap<>(message.getMetadata());

	}

	/**
	 * Convert to Spring AI UserMessage.
	 */
	public UserMessage toUserMessage() {
		// UserMessage constructor just takes content as String
		return new UserMessage(this.content);
	}



	/**
	 * DTO for Media within UserMessage.
	 * Placeholder for future media support.
	 */
	@Data
	public static class MediaDTO {

		private String mimeType;

		private Object data;
	}
}
