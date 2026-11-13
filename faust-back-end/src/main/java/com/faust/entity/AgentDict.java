package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据字典表
 */
@Data
@TableName("agent_dict")
public class AgentDict {

	@TableId(type = IdType.AUTO)
	private Long id;

	/**
	 * 字典类型：classification / agent_tags / tool_tags /
	 */
	private String dictType;

	/**
	 * 字典编码
	 */
	private String dictCode;

	/**
	 * 字典标签（中文描述）
	 */
	private String dictLabel;

	/**
	 * 排序号
	 */
	private Integer sortOrder;


	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private String createBy;

	private String updateBy;

}
