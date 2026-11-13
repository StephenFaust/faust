package com.faust.dto.space.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "工作空间信息")
public class SpaceInfoResp {

	@Schema(description = "空间 ID")
	private Long spaceId;

	@Schema(description = "空间名称")
	private String name;

	@Schema(description = "空间类型：PERSONAL / TEAM")
	private String spaceType;

	@Schema(description = "空间类型描述")
	private String spaceTypeDesc;

	@Schema(description = "所有者 UCID")
	private Long ownerUcId;

	@Schema(description = "所有者昵称")
	private String ownerName;

	@Schema(description = "空间描述")
	private String description;

	@Schema(description = "当前用户在该空间的角色：OWNER / MEMBER")
	private String role;

	@Schema(description = "当前用户是否为创建者")
	private Boolean owner;

	@Schema(description = "是否为个人空间")
	private Boolean personal;

	@Schema(description = "成员数")
	private Long memberCount;

	@Schema(description = "Agent 数量")
	private Long agentCount;

	@Schema(description = "工具数量")
	private Long toolCount;

	@Schema(description = "技能数量")
	private Long skillCount;

	@Schema(description = "数据源数量")
	private Long dataSourceCount;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;
}
