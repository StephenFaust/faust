package com.faust.dto.chat.req;


import com.faust.dto.file.req.FileInfoReq;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "新建会话请求")
@AllArgsConstructor
@NoArgsConstructor
public class ChatReq {

	@Schema(description = "用户ID")
	private Long ucId;

	@NotBlank(message = "Agent Code不能为空")
	@Schema(description = "Agent Code")
	private String agentCode;

	@NotBlank(message = "agent 版本不能为空")
	@Schema(description = "version")
	private String version;

	@NotBlank(message = "用户消息不能为空")
	@Schema(description = "用户消息")
	private String userMsg;

	@Schema(description = "文件信息")
	private List<FileInfoReq> fileInfoList;

	@Schema(description = "对话id")
	private String chatId;

	/**
	 * 环境变量：null 表示调用方没有表态（新会话继承用户级全局变量、已有会话保持不变）；
	 * 空 Map 表示显式清空
	 */
	@Schema(description = "环境变量")
	private Map<String, String> envVar;
}
