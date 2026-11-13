package com.faust.controller;

import com.faust.common.api.ApiResponse;
import com.faust.common.context.UserContext;
import com.faust.dto.env.req.GlobalEnvVarSaveReq;
import com.faust.service.GlobalEnvConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/global/env")
@Tag(name = "全局环境变量", description = "Agent + 用户维度的全局环境变量，新建会话时带入会话")
public class GlobalEnvConfigController {

	private final GlobalEnvConfigService globalEnvConfigService;

	@GetMapping
	@Operation(summary = "查询全局环境变量", description = "查询当前登录用户在某 Agent 下的全局环境变量")
	public ApiResponse<Map<String, String>> detail(
		@Parameter(description = "Agent 编码", required = true) @RequestParam("agentCode") String agentCode) {
		return ApiResponse.success(globalEnvConfigService.getEnvVar(agentCode, UserContext.getUcId()));
	}

	@PostMapping("/save")
	@Operation(summary = "保存全局环境变量", description = "全量替换当前用户在某 Agent 下的全局环境变量，传空对象表示清空")
	public ApiResponse<Boolean> save(@RequestBody GlobalEnvVarSaveReq request) {
		return ApiResponse.success(globalEnvConfigService.save(request, UserContext.getUcId()));
	}
}
