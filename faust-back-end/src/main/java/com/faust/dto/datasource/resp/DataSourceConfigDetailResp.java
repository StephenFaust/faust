package com.faust.dto.datasource.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "数据源配置详情（不含密码）")
public class DataSourceConfigDetailResp {

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

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "创建人")
	private Long createBy;

	@Schema(description = "更新人")
	private Long updateBy;
}
