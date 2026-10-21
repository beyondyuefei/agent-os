package com.kuoge.agentos.business;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import com.kuoge.agentos.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceOrchestratorTest {

    static class AutoApprovalGateway extends HumanApprovalGateway {
        AutoApprovalGateway() {
            // Auto-approve all requests asynchronously
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }).start();
        }

        @Override
        public CompletableFuture<ApprovalResult> requestApproval(String taskId, String taskDescription) {
            CompletableFuture<ApprovalResult> future = new CompletableFuture<>();
            future.complete(new ApprovalResult(true, "auto-approved", "system"));
            return future;
        }
    }

    @Test
    void testProductLifecycleWorkflow() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool("verify_certification", "Verify certifications"));
        tools.register(tool("check_category", "Check category compliance"));
        tools.register(tool("analyze_images", "Analyze product images"));
        tools.register(tool("query_cost", "Query product cost"));
        tools.register(tool("query_logistics", "Query logistics cost"));
        tools.register(tool("query_commission", "Query platform commission"));
        tools.register(tool("query_stock", "Query stock"));
        tools.register(tool("forecast_demand", "Forecast demand"));
        tools.register(tool("generate_title", "Generate listing title"));
        tools.register(tool("generate_keywords", "Generate keywords"));
        tools.register(tool("generate_blog", "Generate blog topic"));
        tools.register(tool("keyword_research", "Research keywords"));
        tools.register(tool("optimize_meta", "Optimize meta"));
        tools.register(tool("generate_review", "Generate review"));
        tools.register(tool("generate_social_post", "Generate social post"));
        tools.register(tool("build_email_sequence", "Build email sequence"));
        tools.register(tool("segment_users", "Segment users"));
        tools.register(tool("query_gmv", "Query GMV"));
        tools.register(tool("query_traffic", "Query traffic"));
        tools.register(tool("query_conversion", "Query conversion"));

        LlmClient llm = MockLlmClient.keyword(Map.of(
                "admission", "Admission verdict: PASSED",
                "profit", "Profit margin: 35%, safe to proceed",
                "inventory", "Inventory risk: LOW",
                "growth", "Growth plan: optimize title + SEO + UGC",
                "seo", "SEO keywords added",
                "ugc", "UGC posts generated",
                "private", "Email sequence built",
                "review", "7-day GMV up 12%, conversion up 0.5%"
        ));

        HumanApprovalGateway gateway = new AutoApprovalGateway();
        EventBus eventBus = new EventBus();
        MarketplaceOrchestrator orchestrator = new MarketplaceOrchestrator(llm, tools, gateway, eventBus);

        DagScheduler.DagExecutionResult result = orchestrator.runProductLifecycle(
                "tenant-1", "ORTH-LOAFER-US-08", "ORTH-LOAFER", "US");

        assertTrue(result.allSuccess(), "All tasks should succeed: " + result.executedTasks());
        assertEquals(8, result.results().size());
        assertTrue(result.results().containsKey("t8-review"));
    }

    private static Tool tool(String name, String description) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public String execute(Map<String, Object> args) { return name + " executed"; }
        };
    }
}
