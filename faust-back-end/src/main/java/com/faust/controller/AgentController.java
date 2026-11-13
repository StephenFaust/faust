package com.faust.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.dto.agent.req.AgentCreateReq;
import com.faust.dto.agent.req.AgentListQueryReq;
import com.faust.dto.agent.req.AgentPublishOrTaskOffReq;
import com.faust.dto.agent.req.AgentUpdateReq;
import com.faust.dto.agent.resp.AgentBaseInfoResp;
import com.faust.dto.agent.resp.AgentCardInfoResp;
import com.faust.dto.agent.resp.AgentInfoDetailResp;
import com.faust.dto.agent.resp.AgentPageInfoResp;
import com.faust.common.api.ApiResponse;
import com.faust.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
@Tag(name = "Agent 管理", description = "Agent 的创建、更新、发布、查询等操作")
public class AgentController {


	private final AgentService agentService;

	@PostMapping("/create")
	@Operation(summary = "创建 Agent", description = "创建一个新的 Agent，包含基本信息、模型配置、提示词、技能和工具绑定")
	public ApiResponse<AgentBaseInfoResp> create(@RequestBody @Valid AgentCreateReq request) {
		return ApiResponse.success(agentService.createAgent(request));
	}

	@PostMapping("/update")
	@Operation(summary = "更新 Agent", description = "全量更新 Agent 信息，技能和工具绑定使用全量替换策略")
	public ApiResponse<Boolean> update(@RequestBody @Valid AgentUpdateReq request) {
		return ApiResponse.success(agentService.updateAgent(request));
	}

	@PostMapping("/publish")
	@Operation(summary = "发布 Agent", description = "将指定 Agent 发布为已发布状态，同一 agentCode 下其他已发布版本自动取消发布")
	public ApiResponse<Boolean> publish(@RequestBody @Valid AgentPublishOrTaskOffReq request) {
		return ApiResponse.success(agentService.publish(request.getAgentId()));
	}

	@PostMapping("/takeOff")
	@Operation(summary = "下架 Agent", description = "将指定 Agent 状态改为未发布")
	public ApiResponse<Boolean> takeOff(@RequestBody @Valid AgentPublishOrTaskOffReq request) {
		return ApiResponse.success(agentService.takeOff(request.getAgentId()));
	}


	@GetMapping("/list")
	@Operation(summary = "分页查询 Agent", description = "支持按关键字和状态筛选的分页查询")
	public ApiResponse<Page<AgentPageInfoResp>> list(AgentListQueryReq query) {
		return ApiResponse.success(agentService.pageList(query));
	}


	@GetMapping("card/list")
	@Operation(summary = "已发布 Agent 卡片列表", description = "仅返回已发布的 Agent，用于展示卡片列表")
	public ApiResponse<Page<AgentCardInfoResp>> cardList(AgentListQueryReq query) {
		return ApiResponse.success(agentService.cardPublishedPageList(query));
	}


	@GetMapping("sub/agent/list")
	@Operation(summary = "已发布 子Agent 列表", description = "仅返回已发布的 子Agent，用户工具绑定")
	public ApiResponse<Page<AgentBaseInfoResp>> subAgentList(AgentListQueryReq query) {
		return ApiResponse.success(agentService.publishedSubAgentPageList(query));
	}

	@PostMapping("/delete")
	@Operation(summary = "删除 Agent", description = "逻辑删除指定 Agent 及其关联子表数据")
	public ApiResponse<Boolean> delete(@RequestBody @Valid AgentPublishOrTaskOffReq request) {
		return ApiResponse.success(agentService.deleteAgent(request.getAgentId()));
	}

	@GetMapping("/detail/{agentCode}")
	@Operation(summary = "查询 Agent 详情", description = "按 agentCode 查询 Agent 完整信息，含模型配置、提示词、技能列表、工具列表")
	public ApiResponse<AgentInfoDetailResp> detail(@PathVariable("agentCode") String agentCode) {
		return ApiResponse.success(agentService.detail(agentCode));
	}

	@GetMapping("/base/{agentCode}")
	@Operation(summary = "查询 Agent 详情", description = "按 agentCode 查询 Agent 基础信息")
	public ApiResponse<AgentBaseInfoResp> base(@PathVariable("agentCode") String agentCode) {
		return ApiResponse.success(agentService.getBaseInfo(agentCode));
	}


	@GetMapping("/message/memory")
	@Operation(summary = "会话记忆")
	public ApiResponse<Object> getChatHistory(@RequestParam("chatId") @NotBlank(message = "会话记忆不能为空") String chatId) {
		return ApiResponse.success(agentService.getMemory(chatId));
	}

}
