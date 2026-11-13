package com.faust.dto.space.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "空间成员信息")
public class SpaceMemberResp {

	@Schema(description = "成员记录 ID")
	private Long id;

	@Schema(description = "空间 ID")
	private Long spaceId;

	@Schema(description = "成员 UCID")
	private Long ucId;

	@Schema(description = "成员昵称")
	private String ucName;

	@Schema(description = "角色：OWNER / MEMBER")
	private String role;

	@Schema(description = "角色描述")
	private String roleDesc;

	@Schema(description = "是否创建者")
	private Boolean owner;

	@Schema(description = "加入时间")
	private LocalDateTime createTime;
}
