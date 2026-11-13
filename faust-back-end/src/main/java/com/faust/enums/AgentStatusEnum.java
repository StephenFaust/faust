package com.faust.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 状态枚举，对应 agent_info.status 字段
 * <p>数据库存储值为 {@link #value}（大写字符串），展示用 {@link #label}。</p>
 */
@AllArgsConstructor
@Getter
public enum AgentStatusEnum {

    UNPUBLISHED("UNPUBLISHED", "未发布"),
    PUBLISHED("PUBLISHED", "已发布"),
    DRAFT("DRAFT", "草稿");

    private final String value;
    private final String label;

    public static AgentStatusEnum fromValue(String value) {
        for (AgentStatusEnum status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
