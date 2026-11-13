package com.faust.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.common.api.ApiResponse;
import com.faust.dto.skill.req.SkillCreateReq;
import com.faust.dto.skill.req.SkillIdReq;
import com.faust.dto.skill.req.SkillListQueryReq;
import com.faust.dto.skill.req.SkillUpdateReq;
import com.faust.dto.skill.resp.SkillBaseInfoResp;
import com.faust.dto.skill.resp.SkillDetailResp;
import com.faust.dto.skill.resp.SkillPageInfoResp;
import com.faust.enums.SkillStatusEnum;
import com.faust.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
@Tag(name = "技能管理", description = "技能的创建、更新、发布、查询等操作")
public class SkillController {

	private final SkillService skillService;

	@PostMapping("/create")
	@Operation(summary = "创建技能", description = "创建一个新的技能定义（草稿状态）")
	public ApiResponse<SkillBaseInfoResp> create(@RequestBody @Valid SkillCreateReq request) {
		return ApiResponse.success(skillService.create(request));
	}

	@PostMapping("/update")
	@Operation(summary = "更新技能", description = "更新技能定义及其条目（条目全量替换）")
	public ApiResponse<Boolean> update(@RequestBody @Valid SkillUpdateReq request) {
		return ApiResponse.success(skillService.update(request));
	}

	@PostMapping("/publish")
	@Operation(summary = "发布技能", description = "将技能发布为已发布状态，发布后可被 Agent 关联")
	public ApiResponse<Boolean> publish(@RequestBody @Valid SkillIdReq request) {
		return ApiResponse.success(skillService.publish(request));
	}

	@PostMapping("/takeOff")
	@Operation(summary = "下架技能", description = "将技能状态改为未发布，存在 Agent 关联时不允许下架")
	public ApiResponse<Boolean> takeOff(@RequestBody @Valid SkillIdReq request) {
		return ApiResponse.success(skillService.takeOff(request));
	}

	@GetMapping("/list")
	@Operation(summary = "技能列表", description = "分页查询技能，支持按关键字和状态筛选")
	public ApiResponse<Page<SkillPageInfoResp>> list(SkillListQueryReq query) {
		return ApiResponse.success(skillService.pageList(query));
	}

	@GetMapping("/published-list")
	@Operation(summary = "已发布技能列表", description = "分页查询已发布的技能，支持按关键字搜索")
	public ApiResponse<Page<SkillPageInfoResp>> publishedList(SkillListQueryReq query) {
		query.setStatus(Collections.singletonList(SkillStatusEnum.PUBLISHED.getValue()));
		return ApiResponse.success(skillService.pageList(query));
	}

	@PostMapping("/delete")
	@Operation(summary = "删除技能", description = "删除技能及其条目，已发布或存在 Agent 关联时不允许删除")
	public ApiResponse<Boolean> delete(@RequestBody @Valid SkillIdReq request) {
		return ApiResponse.success(skillService.delete(request));
	}

	@GetMapping("/detail/{skillId}")
	@Operation(summary = "技能详情", description = "按 skillId 查询技能详情，含全部条目")
	public ApiResponse<SkillDetailResp> detail(@PathVariable("skillId") Long skillId) {
		return ApiResponse.success(skillService.detail(skillId));
	}

}
