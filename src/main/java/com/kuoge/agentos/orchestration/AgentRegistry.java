package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.BusinessAgent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心。
 */
public class AgentRegistry {

    private final Map<String, BusinessAgent> agents = new ConcurrentHashMap<>();

    public AgentRegistry register(String type, BusinessAgent agent) {
        agents.put(type, agent);
        return this;
    }

    public Optional<BusinessAgent> resolve(String type) {
        return Optional.ofNullable(agents.get(type));
    }
}
