package com.faust.dto.agent.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentPublishOrTaskOffReq {

    @NotNull(message = "agentId不能为空")
    private Long agentId;

}
