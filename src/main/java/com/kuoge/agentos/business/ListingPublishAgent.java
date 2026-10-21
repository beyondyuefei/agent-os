package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * Listing 刊登 Agent。
 *
 * <p>调用刊登服务将商品发布到目标市场，并将刊登结果写入共享内存。
 * 因涉及真实平台操作，Profile 中默认 requiresApproval=true。
 */
public class ListingPublishAgent extends BaseBusinessAgent {

    public ListingPublishAgent(LlmClient llmClient) {
        super("listing-publish", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("publish_listing", "sync_images", "update_inventory")));

        String sku = String.valueOf(context.metadata().getOrDefault("sku", "UNKNOWN"));
        String targetMarket = String.valueOf(context.metadata().getOrDefault("targetMarket", "US"));

        String prompt = """
                You are a marketplace listing publisher.
                Publish the product listing for the given SKU to the target market.
                Sync images and update inventory before publishing.
                Return the published listing ID and a short summary.
                """;

        AgentResult result = runWithRuntime(context, prompt, tools);

        if (result.success() && context.sharedMemory() != null) {
            context.sharedMemory().write(agentType, "published-" + sku,
                    targetMarket + ":" + result.output());
        }

        return result;
    }
}
