package com.faust.dto.chat.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResp {

	private String chatId;

	/**
	 * 错误信息（非空时表示对话调用失败）
	 */
	private String errorMsg;


	private String agentResp;

}
