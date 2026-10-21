package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.profile.AgentProfile;
import com.kuoge.agentos.orchestration.plan.ExecutionPlan;
import com.kuoge.agentos.orchestration.plan.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态 DAG 构建引擎。
 *
 * <p>根据 PlanningAgent 生成的 {@link ExecutionPlan}，结合 {@link AgentProfileRegistry}
 * 动态生成 {@link ExecutionGraph}。支持批量 SKU：为每个 SKU 生成独立的子任务副本，
 * 同时保留跨 SKU 的共享阶段（如 product-research）。
 */
@Slf4j
@Component
public class DynamicDagEngine {

    private final AgentProfileRegistry profileRegistry;

    public DynamicDagEngine(AgentProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    /**
     * 将执行计划转换为可执行的 DAG。
     */
    public ExecutionGraph buildGraph(ExecutionPlan plan) {
        ExecutionGraph graph = new ExecutionGraph();

        // 第一阶段：注册所有子任务节点
        Map<String, SubTask> taskById = new LinkedHashMap<>();
        for (SubTask subTask : plan.subTasks()) {
            taskById.put(subTask.taskId(), subTask);
        }

        // 第二阶段：按依赖顺序添加任务，确保前置任务先加入图
        Set<String> added = new HashSet<>();
        List<SubTask> pending = new ArrayList<>(plan.subTasks());

        while (!pending.isEmpty()) {
            boolean progressed = false;
            Iterator<SubTask> it = pending.iterator();
            while (it.hasNext()) {
                SubTask subTask = it.next();
                if (added.containsAll(subTask.dependsOn())) {
                    graph.addTask(toAgentTask(subTask, plan));
                    added.add(subTask.taskId());
                    it.remove();
                    progressed = true;
                }
            }
            if (!progressed) {
                log.error("Cycle or unresolved dependencies in execution plan: {}", pending);
                throw new IllegalStateException("Cannot resolve task dependencies: " + pending);
            }
        }

        return graph;
    }

    /**
     * 为批量 SKU 快速生成标准经营闭环计划。
     *
     * @param workflowId     工作流 ID
     * @param tenantId       租户 ID
     * @param targetMarket   目标市场，如 US
     * @param candidateSkus  候选 SKU 列表
     * @return ExecutionPlan
     */
    public ExecutionPlan buildStandardLifecyclePlan(String workflowId, String tenantId,
                                                     String targetMarket, List<String> candidateSkus) {
        List<SubTask> subTasks = new ArrayList<>();

        // 共享研究节点
        subTasks.add(SubTask.builder()
                .taskId("research")
                .agentType("product-research")
                .instruction("Research market trends and identify candidate SKUs for " + targetMarket)
                .params(Map.of("targetMarket", targetMarket, "expectedCount", candidateSkus.size()))
                .build());

        int index = 1;
        for (String sku : candidateSkus) {
            String prefix = "sku" + index + "-" + sku;

            subTasks.add(SubTask.builder()
                    .taskId(prefix + "-admission")
                    .agentType("product-admission")
                    .instruction("Evaluate product admission for SKU=" + sku + " in " + targetMarket)
                    .params(Map.of("sku", sku, "targetMarket", targetMarket))
                    .dependsOn(Set.of("research"))
                    .sku(sku)
                    .build());

            subTasks.add(SubTask.builder()
                    .taskId(prefix + "-profit")
                    .agentType("profit-guard")
                    .instruction("Compute profit margin for SKU=" + sku)
                    .params(Map.of("sku", sku))
                    .dependsOn(Set.of(prefix + "-admission"))
                    .sku(sku)
                    .build());

            subTasks.add(SubTask.builder()
                    .taskId(prefix + "-inventory")
                    .agentType("inventory-risk")
                    .instruction("Assess inventory risk for SKU=" + sku)
                    .params(Map.of("sku", sku))
                    .dependsOn(Set.of(prefix + "-admission"))
                    .sku(sku)
                    .build());

            subTasks.add(SubTask.builder()
                    .taskId(prefix + "-publish")
                    .agentType("listing-publish")
                    .instruction("Publish listing for SKU=" + sku + " to " + targetMarket)
                    .params(Map.of("sku", sku, "targetMarket", targetMarket))
                    .dependsOn(Set.of(prefix + "-profit", prefix + "-inventory"))
                    .sku(sku)
                    .build());

            index++;
        }

        return ExecutionPlan.builder()
                .workflowId(workflowId)
                .tenantId(tenantId)
                .userGoal("Auto-select and list " + candidateSkus.size() + " SKUs to " + targetMarket)
                .targetMarket(targetMarket)
                .targetSkuCount(candidateSkus.size())
                .subTasks(subTasks)
                .metadata(Map.of("source", "dynamic-engine", "skus", candidateSkus))
                .build();
    }

    private AgentTask toAgentTask(SubTask subTask, ExecutionPlan plan) {
        AgentProfile profile = profileRegistry.resolve(subTask.agentType()).orElse(null);
        boolean requiresApproval = subTask.requiresApproval();
        List<String> allowedTools = List.of();

        if (profile != null) {
            requiresApproval = subTask.requiresApproval() || profile.requiresApproval();
            allowedTools = List.copyOf(profile.allowedTools());
        }

        String enrichedInstruction = enrichInstruction(subTask, plan);

        Map<String, Object> checkpoint = subTask.checkpointId().isBlank()
                ? Map.of()
                : Map.of("type", "rule", "mustContain", subTask.checkpointId());

        return AgentTask.builder()
                .taskId(subTask.taskId())
                .taskType(subTask.agentType())
                .agentType(subTask.agentType())
                .instruction(enrichedInstruction)
                .dependsOn(subTask.dependsOn())
                .requiresApproval(requiresApproval)
                .allowedTools(allowedTools)
                .checkpointConfig(checkpoint)
                .build();
    }

    private String enrichInstruction(SubTask subTask, ExecutionPlan plan) {
        String instruction = subTask.instruction();
        if (instruction == null || instruction.isBlank()) {
            instruction = "Execute " + subTask.agentType() + " task for goal: " + plan.userGoal();
        }
        return instruction;
    }
}
