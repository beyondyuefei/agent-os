package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.memory.InMemorySharedMemory;
import com.kuoge.agentos.core.memory.SharedMemory;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductResearchAgentTest {

    @Test
    void testWritesCandidatesToSharedMemory() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Tool() {
            @Override public String name() { return "research_market"; }
            @Override public String execute(Map<String, Object> args) { return "trend: comfortable shoes"; }
        });
        tools.register(new Tool() {
            @Override public String name() { return "score_product"; }
            @Override public String execute(Map<String, Object> args) { return "85"; }
        });
        tools.register(new Tool() {
            @Override public String name() { return "analyze_competition"; }
            @Override public String execute(Map<String, Object> args) { return "medium"; }
        });

        LlmClient llm = MockLlmClient.directText("Candidate SKUs: SKU-A, SKU-B, SKU-C");
        ProductResearchAgent agent = new ProductResearchAgent(llm);

        SharedMemory memory = new InMemorySharedMemory();
        AgentContext context = new AgentContext(
                "t1", "u1", "s1", "task-1", "Research US market",
                tools, Map.of("targetMarket", "US"), memory);

        AgentResult result = agent.execute(context);

        assertTrue(result.success());
        assertTrue(memory.read("candidateSkus").isPresent());
        assertTrue(memory.read("candidateSkus").get().contains("SKU-A"));
    }
}
