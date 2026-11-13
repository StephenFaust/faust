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
 * Agent 全局环境变量（Agent + 用户维度，一个 Agent 对一个用户一行）
 *
 * <p>与 {@code conversation_record.env_var} 的区别：</p>
 * <ul>
 *   <li>本表是"某 Agent 在某用户下的默认值"，新建该 Agent 的会话时带入；</li>
 *   <li>带入后环境变量仍落在会话记录里，会话内的修改只影响该会话。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "agent_global_env_config", autoResultMap = true)
public class AgentGlobalEnvConfig {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 所属 Agent 编码
	 */
	private String agentCode;

	/**
	 * 所属用户 UCID
	 */
	private Long ucId;

	/**
	 * 全局环境变量
	 */
	@TableField(typeHandler = JsonMapTypeHandler.class)
	private Map<String, String> envVar;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
