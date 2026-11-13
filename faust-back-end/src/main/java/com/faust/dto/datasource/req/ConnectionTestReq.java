package com.faust.dto.datasource.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnectionTestReq {
	@NotBlank(message = "数据源类型不能为空")
	private String dbType;
	@NotBlank(message = "数据源链接不能为空")
	private String url;
	@NotBlank(message = "数据源用户名不能为空")
	private String user;
	@NotBlank(message = "数据源密码不能为空")
	private String pwd;
}
