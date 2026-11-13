package com.faust.dto.datasource.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "删除数据源配置请求")
public class DataSourceConfigDeleteReq {

	@NotNull(message = "id不能为空")
	@Schema(description = "数据源配置主键ID")
	private Long id;
}
