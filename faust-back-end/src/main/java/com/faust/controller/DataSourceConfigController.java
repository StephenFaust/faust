package com.faust.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.dto.datasource.req.*;
import com.faust.dto.datasource.resp.ConnectionTestResp;
import com.faust.dto.datasource.resp.DataSourceConfigBaseInfoResp;
import com.faust.dto.datasource.resp.DataSourceConfigDetailResp;
import com.faust.dto.datasource.resp.DataSourceConfigPageInfoResp;
import com.faust.enums.ErrorMessageEnum;
import com.faust.service.DataSourceConfigService;
import com.faust.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
@Tag(name = "数据源管理", description = "数据源配置的新增、编辑、删除、列表与详情查询")
public class DataSourceConfigController {

	private final DataSourceConfigService dataSourceConfigService;

	@PostMapping("/create")
	@Operation(summary = "新增数据源", description = "创建一条数据源配置记录")
	public ApiResponse<DataSourceConfigBaseInfoResp> create(@RequestBody @Valid DataSourceConfigCreateReq request) {
		return ApiResponse.success(dataSourceConfigService.create(request));
	}

	@PostMapping("/update")
	@Operation(summary = "编辑数据源", description = "更新指定数据源配置，密码留空则保留原值")
	public ApiResponse<Boolean> update(@RequestBody @Valid DataSourceConfigUpdateReq request) {
		return ApiResponse.success(dataSourceConfigService.update(request));
	}

	@PostMapping("/delete")
	@Operation(summary = "删除数据源", description = "删除指定数据源配置")
	public ApiResponse<Boolean> delete(@RequestBody @Valid DataSourceConfigDeleteReq request) {
		return ApiResponse.success(dataSourceConfigService.delete(request));
	}

	@GetMapping("/list")
	@Operation(summary = "数据源列表", description = "分页查询数据源配置，支持按关键字模糊搜索 dbType/url/user")
	public ApiResponse<Page<DataSourceConfigPageInfoResp>> list(DataSourceConfigListQueryReq query) {
		return ApiResponse.success(dataSourceConfigService.pageList(query));
	}

	@GetMapping("/detail/{id}")
	@Operation(summary = "数据源详情", description = "按主键查询数据源配置详情（不含密码）")
	public ApiResponse<DataSourceConfigDetailResp> detail(@PathVariable("id") Long id) {
		return ApiResponse.success(dataSourceConfigService.detail(id));
	}

	@PostMapping("/connection/test")
	@Operation(summary = "测试数据源链接", description = "测试数据源链接是否成功")
	public ApiResponse<Boolean> connectionTest(@Valid @RequestBody ConnectionTestReq req) {
		ConnectionTestResp connectionTestResp = dataSourceConfigService.connectionTest(req);
		if (Boolean.TRUE.equals(connectionTestResp.getResult())) {
			return ApiResponse.success(true);
		}
		return ApiResponse.error(ErrorMessageEnum.DATA_SOURCE_CONNECT_FAILED.getErrorCode(), connectionTestResp.getErrMsg());
	}

}
