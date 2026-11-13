package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间成员：个人空间同样有一条 role = OWNER 的记录，
 * 所有空间鉴权统一走这张表，避免按空间类型分支判断。
 */
@Data
@TableName("work_space_user")
public class WorkSpaceUser {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long spaceId;

	private Long ucId;

	private String ucName;

	/**
	 * 角色：OWNER / MEMBER，见 {@link com.faust.enums.SpaceRoleEnum}
	 */
	private String role;

	private LocalDateTime createTime;

	/**
	 * 邀请人 UCID
	 */
	private Long createBy;
}
