package com.faust.controller;

import com.faust.common.api.ApiResponse;
import com.faust.dto.space.req.SpaceCreateReq;
import com.faust.dto.space.req.SpaceIdReq;
import com.faust.dto.space.req.SpaceMemberAddReq;
import com.faust.dto.space.req.SpaceMemberOperateReq;
import com.faust.dto.space.req.SpaceRenameReq;
import com.faust.dto.space.resp.SpaceInfoResp;
import com.faust.dto.space.resp.SpaceMemberResp;
import com.faust.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作空间管理：个人空间自动生成，团队空间可新建 / 重命名 / 解散，成员可加人 / 退出。
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/space")
@Tag(name = "工作空间", description = "工作空间的创建、成员管理与解散")
public class SpaceController {

	private final SpaceService spaceService;

	@GetMapping("/list")
	@Operation(summary = "我的空间列表", description = "个人空间 + 我创建的团队空间 + 我加入的团队空间；首次调用会自动创建个人空间")
	public ApiResponse<List<SpaceInfoResp>> list() {
		return ApiResponse.success(spaceService.mySpaces());
	}

	@GetMapping("/detail")
	@Operation(summary = "空间详情", description = "包含成员数与各类资源数量，用于解散前提示")
	public ApiResponse<SpaceInfoResp> detail(@RequestParam("spaceId") Long spaceId) {
		return ApiResponse.success(spaceService.detail(spaceId));
	}

	@PostMapping("/create")
	@Operation(summary = "新建团队空间")
	public ApiResponse<SpaceInfoResp> create(@RequestBody @Valid SpaceCreateReq request) {
		return ApiResponse.success(spaceService.create(request));
	}

	@PostMapping("/rename")
	@Operation(summary = "重命名空间", description = "仅创建者可操作，个人空间不支持")
	public ApiResponse<Boolean> rename(@RequestBody @Valid SpaceRenameReq request) {
		return ApiResponse.success(spaceService.rename(request));
	}

	@PostMapping("/dissolve")
	@Operation(summary = "解散空间", description = "仅创建者可操作；空间内仍有资源时拒绝解散")
	public ApiResponse<Boolean> dissolve(@RequestBody @Valid SpaceIdReq request) {
		return ApiResponse.success(spaceService.dissolve(request.getSpaceId()));
	}

	@GetMapping("/member/list")
	@Operation(summary = "空间成员列表")
	public ApiResponse<List<SpaceMemberResp>> memberList(@RequestParam("spaceId") Long spaceId) {
		return ApiResponse.success(spaceService.members(spaceId));
	}

	@PostMapping("/member/add")
	@Operation(summary = "添加成员", description = "空间内成员均可添加")
	public ApiResponse<Boolean> addMember(@RequestBody @Valid SpaceMemberAddReq request) {
		return ApiResponse.success(spaceService.addMember(request));
	}

	@PostMapping("/member/remove")
	@Operation(summary = "移除成员", description = "仅创建者可操作，且不能移除自己")
	public ApiResponse<Boolean> removeMember(@RequestBody @Valid SpaceMemberOperateReq request) {
		return ApiResponse.success(spaceService.removeMember(request.getSpaceId(), request.getUcId()));
	}

	@PostMapping("/member/leave")
	@Operation(summary = "退出空间", description = "创建者不能退出，需先解散空间")
	public ApiResponse<Boolean> leave(@RequestBody @Valid SpaceIdReq request) {
		return ApiResponse.success(spaceService.leave(request.getSpaceId()));
	}
}
