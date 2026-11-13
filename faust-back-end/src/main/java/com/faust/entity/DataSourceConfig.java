package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_source_config")
public class DataSourceConfig {

	@TableId(type = IdType.AUTO)
	private Long id;
	/**
	 * 所属工作空间，见 work_space.id
	 */
	private Long workspaceId;
	private String dbName;
	private String dbType;
	private String url;
	private String user;
	private String pwd;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private Long updateBy;
	private Long createBy;

}
