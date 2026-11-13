package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent系统提示词表
 * <p>version = -1 表示当前活跃/可编辑版本；version >= 1 表示历史版本快照（每次编辑自动创建）。</p>
 */
@Data
@TableName("agent_system_prompt")
public class AgentSystemPrompt {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 关联 agent_info.id
	 */
	private Long agentId;


	/**
	 * 提示词内容（支持 Markdown）
	 */
	private String content;


	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;


}
