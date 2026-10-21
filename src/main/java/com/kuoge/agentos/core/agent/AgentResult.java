package com.kuoge.agentos.core.agent;

import com.kuoge.agentos.core.runtime.TurnSummary;

public record AgentResult(
        String agentType,
        boolean success,
        TurnSummary turnSummary,
        long latencyMs,
        String output
) {

    public AgentResult {
        agentType = agentType == null ? "" : agentType;
        output = output == null ? "" : output;
    }
}
