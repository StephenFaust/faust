package com.faust.dto.space.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "空间成员操作请求（移除成员 / 转让）")
public class SpaceMemberOperateReq {

	@NotNull(message = "spaceId不能为空")
	@Schema(description = "空间 ID")
	private Long spaceId;

	@NotNull(message = "ucId不能为空")
	@Schema(description = "目标用户 UCID")
	private Long ucId;
}
