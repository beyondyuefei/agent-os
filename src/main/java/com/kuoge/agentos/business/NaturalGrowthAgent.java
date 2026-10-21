package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.orchestration.HumanApprovalGateway;

/**
 * 自然流增长 Agent。
 */
public class NaturalGrowthAgent extends BaseBusinessAgent {

    private final HumanApprovalGateway approvalGateway;

    public NaturalGrowthAgent(LlmClient llmClient, HumanApprovalGateway approvalGateway) {
        super("natural-growth", llmClient);
        this.approvalGateway = approvalGateway;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        // In real scenario, request human approval before executing growth plan.
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("generate_title", "generate_keywords", "generate_blog")));
        String prompt = """
                You are a natural growth strategist.
                Create an organic growth plan including listing title, keywords, blog topics and UGC angles.
                Return the plan in structured format.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
