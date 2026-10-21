package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * UGC Agent。
 */
public class UgcAgent extends BaseBusinessAgent {

    public UgcAgent(LlmClient llmClient) {
        super("ugc", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("generate_review", "generate_social_post")));
        String prompt = """
                You are a UGC content generator.
                Create authentic-looking product reviews and social media posts.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
