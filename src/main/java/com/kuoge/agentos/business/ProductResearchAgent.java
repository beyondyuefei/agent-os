package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;

/**
 * 选品研究 Agent。
 *
 * <p>根据目标市场进行趋势分析、竞品分析和 SKU 潜力评分，
 * 输出候选 SKU 列表并写入共享内存，供后续 Agent 消费。
 */
public class ProductResearchAgent extends BaseBusinessAgent {

    public ProductResearchAgent(LlmClient llmClient) {
        super("product-research", llmClient);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ToolRegistry tools = context.toolRegistry().scoped(java.util.Set.copyOf(
                java.util.List.of("research_market", "analyze_competition", "score_product")));

        String prompt = """
                You are a cross-border e-commerce product researcher.
                Analyze market trends, competition, and SKU potential for the given market.
                Return a structured list of candidate SKUs with reasoning.
                If targetSkuCount is provided, aim to return that many candidates.
                """;

        AgentResult result = runWithRuntime(context, prompt, tools);

        // 将研究结果写入共享内存，供下游 Agent 读取
        if (result.success() && context.sharedMemory() != null) {
            context.sharedMemory().write(agentType, "candidateSkus", result.output());
            context.sharedMemory().write(agentType, "researchMarket",
                    String.valueOf(context.metadata().getOrDefault("targetMarket", "US")));
        }

        return result;
    }
}
