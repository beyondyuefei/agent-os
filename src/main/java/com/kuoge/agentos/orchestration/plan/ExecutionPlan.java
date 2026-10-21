package com.kuoge.agentos.orchestration.plan;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 由 PlanningAgent 生成的结构化执行计划。
 *
 * <p>对应 Claude Code 的 /plan 命令输出：把自然语言目标拆分为可验证、可依赖追踪的
 * 子任务集合。主编排器再据此动态构建 DAG 并 spawn 子 Agent。
 */
public record ExecutionPlan(
        String workflowId,
        String tenantId,
        String userGoal,
        String targetMarket,
        Integer targetSkuCount,
        List<SubTask> subTasks,
        Map<String, Object> metadata
) {

    public ExecutionPlan {
        workflowId = workflowId == null ? "" : workflowId;
        tenantId = tenantId == null ? "" : tenantId;
        userGoal = userGoal == null ? "" : userGoal;
        targetMarket = targetMarket == null ? "" : targetMarket;
        targetSkuCount = targetSkuCount == null ? 0 : targetSkuCount;
        subTasks = subTasks == null ? Collections.emptyList() : List.copyOf(subTasks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExecutionPlan empty(String workflowId, String tenantId, String userGoal) {
        return new ExecutionPlan(workflowId, tenantId, userGoal, "", 0, List.of(), Map.of());
    }

    public List<SubTask> subTasksForSku(String sku) {
        return subTasks.stream()
                .filter(t -> sku.equals(t.sku()))
                .toList();
    }

    public static class Builder {
        private String workflowId;
        private String tenantId;
        private String userGoal;
        private String targetMarket;
        private Integer targetSkuCount;
        private List<SubTask> subTasks;
        private Map<String, Object> metadata;

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userGoal(String userGoal) {
            this.userGoal = userGoal;
            return this;
        }

        public Builder targetMarket(String targetMarket) {
            this.targetMarket = targetMarket;
            return this;
        }

        public Builder targetSkuCount(Integer targetSkuCount) {
            this.targetSkuCount = targetSkuCount;
            return this;
        }

        public Builder subTasks(List<SubTask> subTasks) {
            this.subTasks = subTasks;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ExecutionPlan build() {
            return new ExecutionPlan(workflowId, tenantId, userGoal, targetMarket,
                    targetSkuCount, subTasks, metadata);
        }
    }
}
