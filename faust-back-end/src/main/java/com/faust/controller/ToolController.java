package com.faust.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.dto.tool.req.ToolCreateReq;
import com.faust.dto.tool.req.ToolListQueryReq;
import com.faust.dto.tool.req.ToolPublishOrTaskOffReq;
import com.faust.dto.tool.req.ToolUpdateReq;
import com.faust.dto.tool.resp.ToolBaseInfoResp;
import com.faust.dto.tool.resp.ToolDetailResp;
import com.faust.dto.tool.resp.ToolPageInfoResp;
import com.faust.enums.ToolStatusEnum;
import com.faust.common.api.ApiResponse;
import com.faust.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/tool")
@RequiredArgsConstructor
@Tag(name = "工具管理", description = "工具的创建、更新、发布、查询等操作")
public class ToolController {

	private final ToolService toolService;

	@PostMapping("/create")
	@Operation(summary = "创建工具", description = "创建一个新的工具定义（草稿状态）")
	public ApiResponse<ToolBaseInfoResp> create(@RequestBody @Valid ToolCreateReq request) {
		return ApiResponse.success(toolService.create(request));
	}

	@PostMapping("/update")
	@Operation(summary = "更新工具", description = "更新工具定义信息")
	public ApiResponse<Boolean> update(@RequestBody @Valid ToolUpdateReq request) {
		return ApiResponse.success(toolService.update(request));
	}

	@PostMapping("/publish")
	@Operation(summary = "发布工具", description = "将工具发布为已发布状态")
	public ApiResponse<Boolean> publish(@RequestBody @Valid ToolPublishOrTaskOffReq request) {
		return ApiResponse.success(toolService.publish(request));
	}

	@PostMapping("/takeOff")
	@Operation(summary = "下架工具", description = "将工具状态改为未发布")
	public ApiResponse<Boolean> takeOff(@RequestBody @Valid ToolPublishOrTaskOffReq request) {
		return ApiResponse.success(toolService.takeOff(request));
	}

	@GetMapping("/list")
	@Operation(summary = "工具列表", description = "分页查询工具，支持按关键字和状态筛选")
	public ApiResponse<Page<ToolPageInfoResp>> list(ToolListQueryReq query) {
		return ApiResponse.success(toolService.pageList(query));
	}

	@GetMapping("/published-list")
	@Operation(summary = "已发布工具列表", description = "分页查询已发布的工具，支持按关键字搜索")
	public ApiResponse<Page<ToolPageInfoResp>> publishedList(ToolListQueryReq query) {
		query.setStatus(Collections.singletonList(ToolStatusEnum.PUBLISHED.getValue()));
		return ApiResponse.success(toolService.pageList(query));
	}

	@PostMapping("/delete")
	@Operation(summary = "删除工具", description = "逻辑删除指定工具定义")
	public ApiResponse<Boolean> delete(@RequestBody @Valid ToolPublishOrTaskOffReq request) {
		return ApiResponse.success(toolService.delete(request));
	}

	@GetMapping("/detail/{toolId}")
	@Operation(summary = "工具详情", description = "按 toolId 查询工具详情")
	public ApiResponse<ToolDetailResp> detail(@PathVariable("toolId") Long toolId) {
		return ApiResponse.success(toolService.detail(toolId));
	}

}
