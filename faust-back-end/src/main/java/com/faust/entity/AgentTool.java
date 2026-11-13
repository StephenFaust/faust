package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent工具表（工具定义，可发布共享）
 * <p>DRAFT 状态仅创建者 agent 可用，PUBLISHED 后所有 agent 可通过 agent_tool_rel 关联。</p>
 */
@Data
@TableName(value = "agent_tool", autoResultMap = true)
public class AgentTool {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 所属工作空间；0 表示系统内置工具（NATIVE），全局共享且只读
	 */
	private Long workspaceId;

	/**
	 * 工具编码，全局唯一
	 */
	private String toolCode;

	/**
	 * 工具名称
	 */
	private String name;


	/**
	 * 原生工具beanName
	 */
	private String beanName;

	/**
	 * 类型
	 */
	private String type;


	/**
	 * 工具描述
	 */
	private String description;

	/**
	 * 工具 Schema（JSON Schema 格式，含 name / description / parameters）
	 */
	private String toolSchema;

	/**
	 * 数据源配置id
	 */
	private Long dataSourceConfigId;


	/**
	 * 子agentId
	 */
	private Long subAgentId;

	/**
	 * 状态：/ PUBLISHED=已发布 UNPUBLISHED=未发布
	 */
	private String status;

	/**
	 * 发布版本号（仅 PUBLISHED 状态有值）
	 */
	private String publishVersion;


	/**
	 * 标签
	 */
	@TableField(typeHandler = FastjsonTypeHandler.class)
	private List<String> tags;


	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long createBy;

	private Long updateBy;

}
