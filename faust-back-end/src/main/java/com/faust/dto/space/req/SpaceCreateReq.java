package com.faust.dto.space.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "新建团队空间请求")
public class SpaceCreateReq {

	@NotBlank(message = "空间名称不能为空")
	@Size(max = 128, message = "空间名称不能超过128个字符")
	@Schema(description = "空间名称", example = "美租商品组")
	private String name;

	@Size(max = 512, message = "空间描述不能超过512个字符")
	@Schema(description = "空间描述")
	private String description;
}
