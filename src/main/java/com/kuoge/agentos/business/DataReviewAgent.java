package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 数据复盘 Agent。
 */
public class DataReviewAgent extends BaseBusinessAgent {

    public DataReviewAgent(LlmClient llmClient) {
        super("data-review", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("query_gmv", "query_traffic", "query_conversion")));
        String prompt = """
                You are a data analyst.
                Review 7-day GMV, traffic and conversion data, then provide optimization suggestions.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
