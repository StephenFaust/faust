package com.faust.dto.datasource.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "编辑数据源配置请求")
public class DataSourceConfigUpdateReq {

	@NotNull(message = "id不能为空")
	@Schema(description = "数据源配置主键ID")
	private Long id;


	@NotBlank(message = "dbName不能为空")
	@Schema(description = "数据库名称")
	private String dbName;

	@NotBlank(message = "dbType不能为空")
	@Schema(description = "数据库类型，如 MYSQL/POSTGRESQL/ORACLE", example = "MYSQL")
	private String dbType;

	@NotBlank(message = "url不能为空")
	@Schema(description = "JDBC 连接地址", example = "jdbc:mysql://127.0.0.1:3306/test")
	private String url;

	@NotBlank(message = "user不能为空")
	@Schema(description = "连接用户名", example = "root")
	private String user;

	@Schema(description = "连接密码，留空则保留原密码", example = "******")
	private String pwd;
}
