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

class ListingPublishAgentTest {

    @Test
    void testPublishesAndWritesResultToMemory() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Tool() {
            @Override public String name() { return "publish_listing"; }
            @Override public String execute(Map<String, Object> args) {
                return "US-LISTING-" + args.get("sku");
            }
        });
        tools.register(new Tool() {
            @Override public String name() { return "sync_images"; }
            @Override public String execute(Map<String, Object> args) { return "true"; }
        });
        tools.register(new Tool() {
            @Override public String name() { return "update_inventory"; }
            @Override public String execute(Map<String, Object> args) { return "true"; }
        });

        LlmClient llm = MockLlmClient.directText("Listing published successfully");
        ListingPublishAgent agent = new ListingPublishAgent(llm);

        SharedMemory memory = new InMemorySharedMemory();
        AgentContext context = new AgentContext(
                "t1", "u1", "s1", "task-1", "Publish SKU-X to US",
                tools, Map.of("sku", "SKU-X", "targetMarket", "US"), memory);

        AgentResult result = agent.execute(context);

        assertTrue(result.success());
        assertTrue(memory.read("published-SKU-X").isPresent());
        assertTrue(memory.read("published-SKU-X").get().contains("US:"));
    }
}
