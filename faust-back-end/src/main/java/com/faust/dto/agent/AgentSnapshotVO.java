package com.faust.dto.agent;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentSnapshotVO {

    private Long id;
    private Long agentId;
    private String agentCode;
    private String version;
    private String name;
    private String description;
    private String tags;
    private String snapshotDesc;
    private LocalDateTime createTime;
}
