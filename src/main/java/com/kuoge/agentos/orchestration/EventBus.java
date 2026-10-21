package com.kuoge.agentos.orchestration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * 内存事件总线 —— 用于 Agent 间异步解耦。
 */
@Component
public class EventBus {

    private final Map<String, Set<Consumer<AgentEvent>>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String eventType, Consumer<AgentEvent> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArraySet<>()).add(handler);
    }

    public void publish(String eventType, AgentEvent event) {
        Set<Consumer<AgentEvent>> handlers = subscribers.getOrDefault(eventType, Set.of());
        for (Consumer<AgentEvent> handler : handlers) {
            handler.accept(event);
        }
    }

    public record AgentEvent(String eventType, String taskId, String agentType, Object payload) {}
}
