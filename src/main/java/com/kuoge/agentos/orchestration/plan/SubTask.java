package com.kuoge.agentos.orchestration.plan;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 执行计划中的原子子任务。
 *
 * <p>由 PlanningAgent 根据用户目标动态生成，DagScheduler 将其调度为具体的 AgentTask。
 */
public record SubTask(
        String taskId,
        String agentType,
        String instruction,
        Map<String, Object> params,
        Set<String> dependsOn,
        boolean requiresApproval,
        String checkpointId,
        String sku
) {

    public SubTask {
        taskId = taskId == null ? "" : taskId;
        agentType = agentType == null ? "" : agentType;
        instruction = instruction == null ? "" : instruction;
        params = params == null ? Map.of() : Map.copyOf(params);
        dependsOn = dependsOn == null ? Collections.emptySet() : Set.copyOf(dependsOn);
        checkpointId = checkpointId == null ? "" : checkpointId;
        sku = sku == null ? "" : sku;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String agentType;
        private String instruction;
        private Map<String, Object> params;
        private Set<String> dependsOn;
        private boolean requiresApproval;
        private String checkpointId;
        private String sku;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public Builder dependsOn(Set<String> dependsOn) {
            this.dependsOn = dependsOn;
            return this;
        }

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder checkpointId(String checkpointId) {
            this.checkpointId = checkpointId;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public SubTask build() {
            return new SubTask(taskId, agentType, instruction, params, dependsOn,
                    requiresApproval, checkpointId, sku);
        }
    }
}
