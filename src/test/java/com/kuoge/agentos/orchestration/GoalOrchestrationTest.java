package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.business.*;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import com.kuoge.agentos.orchestration.checkpoint.CheckpointEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class GoalOrchestrationTest {

    static class AutoApprovalGateway extends HumanApprovalGateway {
        @Override
        public CompletableFuture<ApprovalResult> requestApproval(String taskId, String taskDescription) {
            return CompletableFuture.completedFuture(new ApprovalResult(true, "auto-approved", "system"));
        }
    }

    @Test
    void testRunGoalWithFallbackPlan() {
        ToolRegistry tools = buildTools();
        LlmClient llm = MockLlmClient.directText("plain text fallback");

        MarketplaceOrchestrator orchestrator = new MarketplaceOrchestrator(
                llm, tools, new AutoApprovalGateway(), new EventBus(),
                null, null,
                new PlanningAgent(llm),
                new AgentProfileRegistry(),
                new DynamicDagEngine(new AgentProfileRegistry()),
                new CheckpointEvaluator(llm)
        );

        DagScheduler.DagExecutionResult result = orchestrator.runGoal(
                "tenant-1", "List 3 SKUs to US market");

        assertTrue(result.allSuccess(), "All tasks should succeed: " + result.executedTasks());
        assertTrue(result.executedTasks().size() >= 7); // research + 3 tasks * 3 SKUs

        String workflowId = result.executedTasks().get(0).taskId().startsWith("research")
                ? "unknown" : result.executedTasks().get(0).taskId();
        // 至少验证 workflow 执行后有 Kanban 看板与共享内存
        assertTrue(orchestrator.getKanbanBoard(workflowId).isPresent() ||
                orchestrator.getKanbanBoardsSnapshot().size() > 0);
    }

    private ToolRegistry buildTools() {
        ToolRegistry tools = new ToolRegistry();
        register(tools, "research_market", "market research");
        register(tools, "score_product", "score");
        register(tools, "analyze_competition", "competition");
        register(tools, "verify_certification", "cert");
        register(tools, "check_category", "category");
        register(tools, "analyze_images", "images");
        register(tools, "query_cost", "cost");
        register(tools, "query_logistics", "logistics");
        register(tools, "query_commission", "commission");
        register(tools, "query_stock", "stock");
        register(tools, "forecast_demand", "demand");
        register(tools, "publish_listing", "publish");
        register(tools, "sync_images", "sync images");
        register(tools, "update_inventory", "inventory");
        return tools;
    }

    private void register(ToolRegistry registry, String name, String desc) {
        registry.register(new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return desc; }
            @Override public String execute(Map<String, Object> args) { return name + " done"; }
        });
    }
}
