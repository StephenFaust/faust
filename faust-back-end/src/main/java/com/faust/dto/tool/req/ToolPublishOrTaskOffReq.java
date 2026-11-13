package com.faust.dto.tool.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ToolPublishOrTaskOffReq {

	@NotNull(message = "toolId不能为空")
	private Long toolId;

}
