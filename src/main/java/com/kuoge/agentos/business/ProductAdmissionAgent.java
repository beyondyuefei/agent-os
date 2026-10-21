package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 商品经营准入 Agent。
 */
public class ProductAdmissionAgent extends BaseBusinessAgent {

    public ProductAdmissionAgent(LlmClient llmClient) {
        super("product-admission", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("verify_certification", "check_category", "analyze_images")));
        String prompt = """
                You are a product admission specialist for cross-border e-commerce.
                Evaluate compliance, category fit, content assets and logistics readiness.
                Return a concise admission verdict.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
