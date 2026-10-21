package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 私域化运营 Agent。
 */
public class PrivateDomainAgent extends BaseBusinessAgent {

    public PrivateDomainAgent(LlmClient llmClient) {
        super("private-domain", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("build_email_sequence", "segment_users")));
        String prompt = """
                You are a private-domain marketing specialist.
                Build an email sequence and user segmentation plan to improve retention.
                """;
        return runWithRuntime(context, prompt, tools);
    }
}
