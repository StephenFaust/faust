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
 * Agent主表（当前工作版本）
 * <p>同一 agent_code 只能有一个 PUBLISHED 版本，version 由用户指定且同一 agent_code 下唯一。</p>
 */
@Data
@TableName(value = "agent_info", autoResultMap = true)
public class AgentInfo {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 所属工作空间，见 work_space.id
	 */
	private Long workspaceId;

	/**
	 * Agent编码，同一Agent各版本共享
	 */
	private String agentCode;

	/**
	 * Agent名称
	 */
	private String name;

	/**
	 * Agent描述，最长100字符
	 */
	private String description;

	/**
	 * 标签，json
	 */
	@TableField(typeHandler = FastjsonTypeHandler.class)
	private List<String> tags;


	/**
	 * 分类
	 */
	private String classification;

	/**
	 * 使用次数
	 */
	private Integer useNum;

	/**
	 * 用户指定版本号，如 1.0.0、2.1.0
	 */
	private String version;

	/**
	 * 是否可以作为子智能体
	 */
	private Boolean canAsSubAgent;

	/**
	 * 状态：PUBLISHED / UNPUBLISHED
	 */
	private String status;


	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long createBy;

	private Long updateBy;

	@TableField(exist = false)
	private Boolean isSubAgent;
}
