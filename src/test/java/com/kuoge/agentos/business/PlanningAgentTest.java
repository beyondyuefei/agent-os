package com.kuoge.agentos.business;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import com.kuoge.agentos.orchestration.plan.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanningAgentTest {

    @Test
    void testFallbackPlanWhenLlmReturnsNoJson() {
        LlmClient llm = MockLlmClient.directText("I will help you list products");
        PlanningAgent agent = new PlanningAgent(llm);

        ExecutionPlan plan = agent.plan("tenant-1", "wf-1",
                "List 5 SKUs to US market", new ToolRegistry());

        assertEquals("tenant-1", plan.tenantId());
        assertEquals("US", plan.targetMarket());
        assertEquals(5, plan.targetSkuCount());
        assertFalse(plan.subTasks().isEmpty());
        assertTrue(plan.subTasks().stream().anyMatch(t -> "product-research".equals(t.agentType())));
        assertTrue(plan.subTasks().stream().anyMatch(t -> "listing-publish".equals(t.agentType())));
    }

    @Test
    void testParseLlmPlan() {
        LlmClient llm = MockLlmClient.directText("""
                {
                  "targetMarket": "US",
                  "targetSkuCount": 3,
                  "subTasks": [
                    {"taskId": "research", "agentType": "product-research", "instruction": "research", "dependsOn": []},
                    {"taskId": "sku-A-admission", "agentType": "product-admission", "instruction": "admit A", "dependsOn": ["research"], "sku": "A"},
                    {"taskId": "sku-A-publish", "agentType": "listing-publish", "instruction": "publish A", "dependsOn": ["sku-A-admission"], "sku": "A", "requiresApproval": true}
                  ]
                }
                """);
        PlanningAgent agent = new PlanningAgent(llm);

        ExecutionPlan plan = agent.plan("tenant-1", "wf-2",
                "List 3 SKUs to US market", new ToolRegistry());

        assertEquals(3, plan.targetSkuCount());
        assertEquals(3, plan.subTasks().size());
        assertTrue(plan.subTasks().stream().anyMatch(t -> "sku-A-publish".equals(t.taskId()) && t.requiresApproval()));
    }

    @Test
    void testExtractMarketFromGoal() {
        PlanningAgent agent = new PlanningAgent(MockLlmClient.directText("plain text"));
        ExecutionPlan euPlan = agent.plan("t1", "wf-3", "给欧洲站选10个SKU", new ToolRegistry());
        assertEquals("EU", euPlan.targetMarket());

        ExecutionPlan jpPlan = agent.plan("t1", "wf-4", "日本站上架5个SKU", new ToolRegistry());
        assertEquals("JP", jpPlan.targetMarket());
    }
}
