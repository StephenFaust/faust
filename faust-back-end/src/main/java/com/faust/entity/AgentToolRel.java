package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent与工具关联表（多对多关系）
 */
@Data
@TableName("agent_tool_rel")
public class AgentToolRel {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 关联 agent_info.id
	 */
	private Long agentId;

	/**
	 * 关联 agent_tool.id
	 */
	private Long toolId;


	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;


}
