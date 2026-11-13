package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faust.common.typehandler.JsonMapTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户与 Agent 的会话记录表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "conversation_record", autoResultMap = true)
public class AgentConversationRecord {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 用户ID
	 */
	private Long ucId;

	/**
	 * 关联 agent_code.
	 */
	private String agentCode;


	/**
	 * 版本
	 */
	private String version;

	/**
	 * 会话ID（UUID）
	 */
	private String chatId;

	/**
	 * 最近的会话内容（不超过200字）
	 * 会话标题
	 */
	private String title;

	/**
	 * 环境变量
	 */
	@TableField(typeHandler = JsonMapTypeHandler.class)
	private Map<String, String> envVar;


	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
