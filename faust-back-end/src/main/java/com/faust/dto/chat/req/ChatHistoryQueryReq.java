package com.faust.dto.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "聊天历史记录查询请求")
public class ChatHistoryQueryReq {

	@Schema(description = "用户ID")
	private Long ucId;

	@NotBlank(message = "会话id不能为空")
	@Schema(description = "会话ID")
	private String chatId;

	@NotBlank(message = "Agent Code不能为空")
	@Schema(description = "Agent Code")
	private String agentCode;

	@Schema(description = "最后一条消息的消息id")
	private Long lastMessageId = 0L;

	@Schema(description = "每页条数，默认 20")
	private Integer pageSize = 20;
}
