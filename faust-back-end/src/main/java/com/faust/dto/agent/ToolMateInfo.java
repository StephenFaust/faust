package com.faust.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolMateInfo {

	/**
	 * 工具名称
	 */
	private String name;


	/**
	 * 原生工具的beanName
	 */
	private String beanName;


	/**
	 * 工具code
	 */
	private String toolCode;


	/**
	 *
	 */
	private String type;


	/**
	 * 版本
	 */
	private String version;

	/**
	 * 工具描述
	 */
	private String description;

	/**
	 * 工具 Schema（JSON Schema 格式，含 name / description / parameters，仅http类型工具使用）
	 */
	private String toolSchema;

	/**
	 * 关联数据源配置（仅db类型工具使用）
	 */
	private DataSourceInfo dataSourceInfo;


	/**
	 * 子agent信息
	 */
	private SubAgentInfo subAgentInfo;

}
