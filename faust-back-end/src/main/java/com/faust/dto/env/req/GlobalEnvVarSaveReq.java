package com.faust.dto.env.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "保存全局环境变量请求")
public class GlobalEnvVarSaveReq {

	@Schema(description = "所属 Agent 编码", requiredMode = Schema.RequiredMode.REQUIRED)
	private String agentCode;

	@Schema(description = "环境变量 key-value，全量替换；传空对象表示清空")
	private Map<String, String> envVar;
}
