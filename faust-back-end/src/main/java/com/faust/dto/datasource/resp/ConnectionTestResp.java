package com.faust.dto.datasource.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectionTestResp {
	private Boolean result;
	private String errMsg;
}
