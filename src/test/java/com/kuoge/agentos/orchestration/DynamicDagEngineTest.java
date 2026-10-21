package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.orchestration.plan.ExecutionPlan;
import com.kuoge.agentos.orchestration.plan.SubTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DynamicDagEngineTest {

    @Test
    void testBuildStandardLifecyclePlanForBatchSkus() {
        DynamicDagEngine engine = new DynamicDagEngine(new AgentProfileRegistry());
        ExecutionPlan plan = engine.buildStandardLifecyclePlan(
                "wf-1", "tenant-1", "US", List.of("SKU-A", "SKU-B"));

        assertEquals(2, plan.targetSkuCount());
        ExecutionGraph graph = engine.buildGraph(plan);

        assertEquals(9, graph.allTasks().size()); // research + 4 tasks * 2 SKUs
        assertNotNull(graph.getTask("research"));
        assertNotNull(graph.getTask("sku1-SKU-A-admission"));
        assertNotNull(graph.getTask("sku1-SKU-A-profit"));
        assertNotNull(graph.getTask("sku1-SKU-A-publish"));

        AgentTask publishTask = graph.getTask("sku1-SKU-A-publish");
        assertTrue(publishTask.requiresApproval());
        assertTrue(publishTask.allowedTools().contains("publish_listing"));
    }

    @Test
    void testBuildGraphFromCustomPlan() {
        DynamicDagEngine engine = new DynamicDagEngine(new AgentProfileRegistry());
        ExecutionPlan plan = ExecutionPlan.builder()
                .workflowId("wf-2")
                .tenantId("tenant-1")
                .userGoal("custom")
                .subTasks(List.of(
                        SubTask.builder().taskId("t1").agentType("product-research").instruction("r").build(),
                        SubTask.builder().taskId("t2").agentType("product-admission")
                                .instruction("a").dependsOn(Set.of("t1")).sku("S1").build(),
                        SubTask.builder().taskId("t3").agentType("profit-guard")
                                .instruction("p").dependsOn(Set.of("t1")).sku("S1").build(),
                        SubTask.builder().taskId("t4").agentType("listing-publish")
                                .instruction("l").dependsOn(Set.of("t2", "t3")).sku("S1").build()
                ))
                .build();

        ExecutionGraph graph = engine.buildGraph(plan);
        assertEquals(4, graph.allTasks().size());

        AgentTask admission = graph.getTask("t2");
        assertEquals(Set.of("t1"), admission.dependsOn());
        assertTrue(admission.allowedTools().contains("verify_certification"));
    }
}
