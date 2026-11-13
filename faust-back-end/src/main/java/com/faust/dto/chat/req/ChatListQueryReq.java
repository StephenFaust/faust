package com.faust.dto.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "会话列表查询请求")
public class ChatListQueryReq {


	@Schema(description = "用户ID")
	private Long ucId;

	@NotBlank(message = "Agent Code不能为空")
	@Schema(description = "Agent Code")
	private String agentCode;

	private Integer current = 1;

	private Integer pageSize = 10;
}
