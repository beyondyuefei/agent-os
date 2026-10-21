package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * SEO Agent。
 */
public class SeoAgent extends BaseBusinessAgent {

    public SeoAgent(LlmClient llmClient) {
        super("seo", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("keyword_research", "optimize_meta")));
        String prompt = """
                You are an SEO specialist.
                Research keywords and optimize meta title/description for the product listing.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
