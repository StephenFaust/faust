package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间：PERSONAL（个人空间，一人一个）/ TEAM（团队空间，可多个）
 * <p>id = 0 为系统空间，仅存放 NATIVE 工具，全局共享、只读。</p>
 */
@Data
@TableName("work_space")
public class WorkSpace {

	@TableId(type = IdType.AUTO)
	private Long id;

	private String name;

	/**
	 * 类型：PERSONAL / TEAM，见 {@link com.faust.enums.SpaceTypeEnum}
	 */
	private String spaceType;

	/**
	 * 创建者 / 所有者 UCID
	 */
	private Long ownerUcId;

	private String ownerName;

	private String description;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long createBy;

	private Long updateBy;

	/**
	 * 数据库生成列（仅 PERSONAL 行有值，用于唯一约束「一人一个个人空间」），只读不写
	 */
	@TableField(exist = false)
	private Long personalOwner;
}
