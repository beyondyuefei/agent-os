package com.kuoge.agentos.core.agent.profile;

import java.util.Collections;
import java.util.Set;

/**
 * Agent Profile —— 借鉴 Hermes Agent 的 specialized agent identity。
 *
 * <p>每个子 Agent 在创建时携带一份 Profile，定义其系统提示词、可用工具、
 * 是否需人工审批以及模型偏好。Profile 让主编排器能够按需 spawn 出上下文隔离的
 * 专业 Worker。
 */
public record AgentProfile(
        String agentType,
        String name,
        String systemPrompt,
        Set<String> allowedTools,
        boolean requiresApproval,
        String modelHint
) {

    public AgentProfile {
        agentType = agentType == null ? "" : agentType;
        name = name == null ? agentType : name;
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        allowedTools = allowedTools == null ? Collections.emptySet() : Set.copyOf(allowedTools);
        modelHint = modelHint == null ? "" : modelHint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentProfile of(String agentType, String systemPrompt, Set<String> allowedTools) {
        return new AgentProfile(agentType, agentType, systemPrompt, allowedTools, false, "");
    }

    public static class Builder {
        private String agentType;
        private String name;
        private String systemPrompt;
        private Set<String> allowedTools;
        private boolean requiresApproval;
        private String modelHint;

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder allowedTools(Set<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder modelHint(String modelHint) {
            this.modelHint = modelHint;
            return this;
        }

        public AgentProfile build() {
            return new AgentProfile(agentType, name, systemPrompt, allowedTools, requiresApproval, modelHint);
        }
    }
}
