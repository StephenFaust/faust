package com.faust.dto.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "继续会话请求")
public class ChatContinueReq {

	@NotBlank(message = "会话ID不能为空")
	@Schema(description = "会话ID")
	private String chatId;

	@NotBlank(message = "agent 版本不能为空")
	@Schema(description = "version")
	private String version;

	@NotBlank(message = "用户消息不能为空")
	@Schema(description = "用户消息")
	private String userMsg;
}
