package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.agent.BusinessAgent;
import com.kuoge.agentos.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DagSchedulerTest {

    static class DummyAgent implements BusinessAgent {
        private final String type;
        private final String output;

        DummyAgent(String type, String output) {
            this.type = type;
            this.output = output;
        }

        @Override
        public String agentType() { return type; }

        @Override
        public AgentResult execute(AgentContext context) {
            return new AgentResult(type, true, null, 0, output);
        }
    }

    @Test
    void testParallelExecutionWithDependencies() {
        AgentRegistry registry = new AgentRegistry();
        registry.register("a", new DummyAgent("a", "A done"));
        registry.register("b", new DummyAgent("b", "B done"));
        registry.register("c", new DummyAgent("c", "C done"));

        HumanApprovalGateway gateway = new HumanApprovalGateway();
        DagScheduler scheduler = new DagScheduler(registry, gateway, 4, ReplanPolicy.stopOnFailure());

        ExecutionGraph graph = new ExecutionGraph()
                .addTask(AgentTask.builder().taskId("t1").agentType("a").instruction("do A").build())
                .addTask(AgentTask.builder().taskId("t2").agentType("b").instruction("do B").build())
                .addTask(AgentTask.builder().taskId("t3").agentType("c").instruction("do C")
                        .dependsOn(Set.of("t1", "t2")).build());

        AgentContext context = AgentContext.of("t1", "u1", "s1", "wf1", "test", new ToolRegistry());
        DagScheduler.DagExecutionResult result = scheduler.execute(graph, context);

        assertTrue(result.allSuccess());
        assertEquals("A done", result.results().get("t1").output());
        assertEquals("B done", result.results().get("t2").output());
        assertEquals("C done", result.results().get("t3").output());
        scheduler.shutdown();
    }
}
