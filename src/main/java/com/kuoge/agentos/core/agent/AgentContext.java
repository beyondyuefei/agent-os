package com.kuoge.agentos.core.agent;

import com.kuoge.agentos.core.memory.SharedMemory;
import com.kuoge.agentos.core.tool.ToolRegistry;

import java.util.Map;

public record AgentContext(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String instruction,
        ToolRegistry toolRegistry,
        Map<String, Object> metadata,
        SharedMemory sharedMemory
) {

    public AgentContext {
        tenantId = tenantId == null ? "default" : tenantId;
        userId = userId == null ? "" : userId;
        sessionId = sessionId == null ? "" : sessionId;
        taskId = taskId == null ? "" : taskId;
        instruction = instruction == null ? "" : instruction;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        sharedMemory = sharedMemory == null ? SharedMemory.noop() : sharedMemory;
    }

    public static AgentContext of(String tenantId, String userId, String sessionId,
                                  String taskId, String instruction, ToolRegistry toolRegistry) {
        return new AgentContext(tenantId, userId, sessionId, taskId, instruction,
                toolRegistry, Map.of(), SharedMemory.noop());
    }
}
