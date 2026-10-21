package com.kuoge.agentos.core.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认的内存级共享工作内存实现。
 *
 * <p>适用于单进程部署。未来可扩展为基于 Redis / 向量数据库的实现，
 * 以支持多实例横向扩展与长期记忆。
 */
public class InMemorySharedMemory implements SharedMemory {

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void write(String agentType, String key, String value) {
        entries.put(key, MemoryEntry.of(agentType, key, value));
    }

    @Override
    public Optional<String> read(String key) {
        return Optional.ofNullable(entries.get(key)).map(MemoryEntry::value);
    }

    @Override
    public List<MemoryEntry> queryByAgent(String agentType) {
        return entries.values().stream()
                .filter(e -> e.agentType().equals(agentType))
                .sorted(Comparator.comparing(MemoryEntry::createdAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> keys() {
        return List.copyOf(entries.keySet());
    }

    @Override
    public List<MemoryEntry> snapshot() {
        return entries.values().stream()
                .sorted(Comparator.comparing(MemoryEntry::createdAt))
                .collect(Collectors.toList());
    }

    @Override
    public void clear() {
        entries.clear();
    }
}
