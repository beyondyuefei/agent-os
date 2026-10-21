package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 利润红线守护 Agent。
 */
public class ProfitGuardAgent extends BaseBusinessAgent {

    public ProfitGuardAgent(LlmClient llmClient) {
        super("profit-guard", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("query_cost", "query_logistics", "query_commission")));
        String prompt = """
                You are a profit guard specialist.
                Compute product cost, logistics, platform commission, tax and gross margin.
                If margin is below target, mark as risky and explain.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
