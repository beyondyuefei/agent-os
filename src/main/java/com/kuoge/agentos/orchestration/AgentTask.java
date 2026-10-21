package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.AgentResult;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
public record AgentTask(
        String taskId,
        String taskType,
        String agentType,
        String instruction,
        Set<String> dependsOn,
        boolean requiresApproval,
        List<String> allowedTools,
        AgentResult result,
        TaskStatus status,
        String errorMessage,
        Map<String, Object> checkpointConfig
) {

    public AgentTask {
        dependsOn = dependsOn == null ? Collections.emptySet() : Set.copyOf(dependsOn);
        allowedTools = allowedTools == null ? Collections.emptyList() : List.copyOf(allowedTools);
        instruction = instruction == null ? "" : instruction;
        status = status == null ? TaskStatus.PENDING : status;
        checkpointConfig = checkpointConfig == null ? Collections.emptyMap() : Map.copyOf(checkpointConfig);
    }

    public AgentTask withStatus(TaskStatus newStatus) {
        return new AgentTask(taskId, taskType, agentType, instruction, dependsOn,
                requiresApproval, allowedTools, result, newStatus, errorMessage, checkpointConfig);
    }

    public AgentTask withResult(AgentResult result) {
        return new AgentTask(taskId, taskType, agentType, instruction, dependsOn,
                requiresApproval, allowedTools, result, TaskStatus.COMPLETED, errorMessage, checkpointConfig);
    }

    public AgentTask failed(String error) {
        return new AgentTask(taskId, taskType, agentType, instruction, dependsOn,
                requiresApproval, allowedTools, result, TaskStatus.FAILED, error, checkpointConfig);
    }

    public AgentTask waitingApproval() {
        return new AgentTask(taskId, taskType, agentType, instruction, dependsOn,
                requiresApproval, allowedTools, result, TaskStatus.WAITING_APPROVAL, errorMessage, checkpointConfig);
    }
}
