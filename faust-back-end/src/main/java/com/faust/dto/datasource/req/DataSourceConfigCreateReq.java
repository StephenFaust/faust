package com.faust.dto.datasource.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "新增数据源配置请求")
public class DataSourceConfigCreateReq {

	@NotBlank(message = "dbType不能为空")
	@Schema(description = "数据库类型，如 MYSQL/POSTGRESQL/ORACLE", example = "MYSQL")
	private String dbType;

	@NotBlank(message = "dbName不能为空")
	@Schema(description = "数据库名称")
	private String dbName;


	@NotBlank(message = "url不能为空")
	@Schema(description = "JDBC 连接地址", example = "jdbc:mysql://127.0.0.1:3306/test")
	private String url;

	@NotBlank(message = "user不能为空")
	@Schema(description = "连接用户名", example = "root")
	private String user;

	@NotBlank(message = "pwd不能为空")
	@Schema(description = "连接密码", example = "******")
	private String pwd;
}
