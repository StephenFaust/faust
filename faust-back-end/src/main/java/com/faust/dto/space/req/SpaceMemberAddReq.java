package com.faust.dto.space.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "添加空间成员请求")
public class SpaceMemberAddReq {

	@NotNull(message = "spaceId不能为空")
	@Schema(description = "空间 ID")
	private Long spaceId;

	@NotNull(message = "ucId不能为空")
	@Positive(message = "ucId必须为正整数")
	@Schema(description = "被添加用户的 UCID")
	private Long ucId;

	@Size(max = 128, message = "成员昵称不能超过128个字符")
	@Schema(description = "被添加用户昵称（仅用于展示）")
	private String ucName;
}
