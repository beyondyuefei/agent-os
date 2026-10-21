package com.kuoge.agentos.core.memory;

import java.time.Instant;

/**
 * 共享工作内存中的一条记录。
 *
 * <p>借鉴 Hermes Agent 的 shared working memory：所有子 Agent 将关键发现写入内存，
 * 其他 Agent 可按 key 或按生产者检索，实现跨 Agent 的上下文共享。
 */
public record MemoryEntry(
        String key,
        String agentType,
        String value,
        Instant createdAt
) {

    public MemoryEntry {
        key = key == null ? "" : key;
        agentType = agentType == null ? "" : agentType;
        value = value == null ? "" : value;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static MemoryEntry of(String agentType, String key, String value) {
        return new MemoryEntry(key, agentType, value, Instant.now());
    }
}
