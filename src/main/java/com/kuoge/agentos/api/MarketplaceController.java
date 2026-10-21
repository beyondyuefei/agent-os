package com.kuoge.agentos.api;

import com.kuoge.agentos.core.memory.MemoryEntry;
import com.kuoge.agentos.infrastructure.persistence.entity.TaskExecutionEntity;
import com.kuoge.agentos.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.TaskExecutionRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.WorkflowInstanceRepository;
import com.kuoge.agentos.orchestration.DagScheduler;
import com.kuoge.agentos.orchestration.MarketplaceOrchestrator;
import com.kuoge.agentos.orchestration.kanban.KanbanBoard;
import com.kuoge.agentos.orchestration.kanban.KanbanCard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 跨境电商经营工作流 API。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceOrchestrator orchestrator;
    private final WorkflowInstanceRepository workflowRepository;
    private final TaskExecutionRepository taskExecutionRepository;

    /**
     * 固定商品生命周期工作流（兼容入口）。
     */
    @PostMapping("/product-lifecycle")
    public ResponseEntity<Map<String, Object>> runProductLifecycle(
            @RequestParam String tenantId,
            @RequestParam String productSku,
            @RequestParam String productName,
            @RequestParam String targetMarket) {

        DagScheduler.DagExecutionResult result = orchestrator.runProductLifecycle(
                tenantId, productSku, productName, targetMarket);

        return ResponseEntity.ok(Map.of(
                "success", result.allSuccess(),
                "executedTasks", result.executedTasks().size(),
                "results", result.results()
        ));
    }

    /**
     * 动态目标编排入口：意图识别 + 子 Agent 自动生成。
     *
     * <p>示例目标：本周给美国站自动选 20 个潜力 SKU 并上架
     */
    @PostMapping("/goal")
    public ResponseEntity<Map<String, Object>> runGoal(
            @RequestParam String tenantId,
            @RequestParam String goal) {

        DagScheduler.DagExecutionResult result = orchestrator.runGoal(tenantId, goal);

        return ResponseEntity.ok(Map.of(
                "success", result.allSuccess(),
                "executedTasks", result.executedTasks().size(),
                "results", result.results()
        ));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowInstanceEntity>> listWorkflows(@RequestParam String tenantId) {
        return ResponseEntity.ok(workflowRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @GetMapping("/{workflowId}/tasks")
    public ResponseEntity<List<TaskExecutionEntity>> getTasks(@PathVariable String workflowId) {
        return ResponseEntity.ok(taskExecutionRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId));
    }

    /**
     * 获取 Kanban 看板 —— Hermes 风格任务板状态。
     */
    @GetMapping("/{workflowId}/kanban")
    public ResponseEntity<?> getKanban(@PathVariable String workflowId) {
        return orchestrator.getKanbanBoard(workflowId)
                .map(board -> ResponseEntity.ok((Object) board.allCards()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取共享内存快照 —— Agent 间上下文共享内容。
     */
    @GetMapping("/{workflowId}/memory")
    public ResponseEntity<?> getMemory(@PathVariable String workflowId) {
        return orchestrator.getSharedMemory(workflowId)
                .map(memory -> ResponseEntity.ok((Object) memory.snapshot()))
                .orElse(ResponseEntity.notFound().build());
    }
}
