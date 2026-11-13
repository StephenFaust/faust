package com.faust.dto.datasource.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "数据源配置基础信息（新增后返回）")
public class DataSourceConfigBaseInfoResp {

	@Schema(description = "数据源配置主键ID")
	private Long id;
}
