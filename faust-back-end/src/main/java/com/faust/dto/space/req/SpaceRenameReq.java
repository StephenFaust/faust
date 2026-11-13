package com.faust.dto.space.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "空间重命名请求")
public class SpaceRenameReq {

	@NotNull(message = "spaceId不能为空")
	@Schema(description = "空间 ID")
	private Long spaceId;

	@NotBlank(message = "空间名称不能为空")
	@Size(max = 128, message = "空间名称不能超过128个字符")
	@Schema(description = "新空间名称")
	private String name;
}
