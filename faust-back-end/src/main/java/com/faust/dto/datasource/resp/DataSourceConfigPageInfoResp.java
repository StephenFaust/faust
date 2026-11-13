package com.faust.dto.datasource.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "数据源配置分页列表项")
public class DataSourceConfigPageInfoResp {

	@Schema(description = "数据源配置主键ID")
	private Long id;

	@Schema(description = "数据源名称")
	private String dbName;

	@Schema(description = "数据库类型")
	private String dbType;

	@Schema(description = "JDBC 连接地址")
	private String url;

	@Schema(description = "连接用户名")
	private String user;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;
}
