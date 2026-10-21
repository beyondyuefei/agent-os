package com.kuoge.agentos.core.memory;

import java.util.List;
import java.util.Optional;

/**
 * 共享工作内存接口 —— Hermes 风格 Agent 间上下文共享层。
 *
 * <p>主编排器为每个 workflow 创建一份共享内存实例，并注入到所有子 Agent 的上下文中。
 * 子 Agent 通过 write/read/query 共享中间结果，避免每个 Agent 重复请求外部系统。
 */
public interface SharedMemory {

    /**
     * 写入一条记录。若 key 已存在则覆盖。
     */
    void write(String agentType, String key, String value);

    /**
     * 读取指定 key 的最新值。
     */
    Optional<String> read(String key);

    /**
     * 查询由指定 Agent 类型产生的所有记录（按时间升序）。
     */
    List<MemoryEntry> queryByAgent(String agentType);

    /**
     * 列出所有 key。
     */
    List<String> keys();

    /**
     * 获取内存快照，用于持久化或调试。
     */
    List<MemoryEntry> snapshot();

    /**
     * 清空内存。
     */
    void clear();

    /**
     * 返回一个什么都不做的空实现，用于兼容未启用共享内存的场景。
     */
    static SharedMemory noop() {
        return new SharedMemory() {
            @Override public void write(String agentType, String key, String value) {}
            @Override public Optional<String> read(String key) { return Optional.empty(); }
            @Override public List<MemoryEntry> queryByAgent(String agentType) { return List.of(); }
            @Override public List<String> keys() { return List.of(); }
            @Override public List<MemoryEntry> snapshot() { return List.of(); }
            @Override public void clear() {}
        };
    }
}
