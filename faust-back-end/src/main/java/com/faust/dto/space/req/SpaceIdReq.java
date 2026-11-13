package com.faust.dto.space.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "空间 ID 请求")
public class SpaceIdReq {

	@NotNull(message = "spaceId不能为空")
	@Schema(description = "空间 ID")
	private Long spaceId;
}
