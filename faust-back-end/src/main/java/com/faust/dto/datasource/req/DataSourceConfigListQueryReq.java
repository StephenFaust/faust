package com.faust.dto.datasource.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "数据源配置分页查询请求")
public class DataSourceConfigListQueryReq {

	@Schema(description = "关键字，模糊匹配 dbType / url / user")
	private String keyword;

	@Schema(description = "当前页，从 1 开始", example = "1")
	private Long current = 1L;

	@Schema(description = "每页条数", example = "20")
	private Long size = 20L;
}
