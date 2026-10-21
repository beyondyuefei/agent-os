package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.agent.BusinessAgent;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.runtime.ConversationRuntime;
import com.kuoge.agentos.core.runtime.RuntimeConfig;
import com.kuoge.agentos.core.runtime.TurnSummary;
import com.kuoge.agentos.core.session.AgentSession;
import com.kuoge.agentos.core.tool.ToolRegistry;

public abstract class BaseBusinessAgent implements BusinessAgent {

    protected final LlmClient llmClient;
    protected final String agentType;

    protected BaseBusinessAgent(String agentType, LlmClient llmClient) {
        this.agentType = agentType;
        this.llmClient = llmClient;
    }

    @Override
    public String agentType() {
        return agentType;
    }

    protected AgentResult runWithRuntime(AgentContext context, String systemPrompt, ToolRegistry scopedTools) {
        long start = System.currentTimeMillis();
        RuntimeConfig config = RuntimeConfig.builder()
                .systemPrompt(systemPrompt)
                .maxIterationsPerTurn(5)
                .maxTurnsPerSession(5)
                .build();
        AgentSession session = new AgentSession();
        ConversationRuntime runtime = new ConversationRuntime(session, llmClient, scopedTools, config);
        TurnSummary summary = runtime.runTurn(context.instruction());
        long latency = System.currentTimeMillis() - start;
        boolean success = "completed".equals(summary.stopReason());
        return new AgentResult(agentType, success, summary, latency, summary.finalAnswer());
    }
}
