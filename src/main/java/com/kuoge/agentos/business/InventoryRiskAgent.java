package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 库存风控 Agent。
 */
public class InventoryRiskAgent extends BaseBusinessAgent {

    public InventoryRiskAgent(LlmClient llmClient) {
        super("inventory-risk", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("query_stock", "forecast_demand")));
        String prompt = """
                You are an inventory risk analyst.
                Check current stock and forecast demand, then flag overstock or stockout risks.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
