package com.kuoge.agentos.infrastructure.persistence;

import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.infrastructure.persistence.entity.TaskExecutionEntity;
import com.kuoge.agentos.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.TaskExecutionRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.WorkflowInstanceRepository;
import com.kuoge.agentos.orchestration.AgentTask;
import com.kuoge.agentos.orchestration.DagScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作流持久化服务。
 */
@Service
@RequiredArgsConstructor
public class WorkflowPersistenceService {

    private final WorkflowInstanceRepository workflowRepository;
    private final TaskExecutionRepository taskExecutionRepository;

    @Transactional
    public void saveWorkflowInstance(String workflowId, String tenantId, String userId,
                                      String productSku, String productName, String targetMarket,
                                      String status) {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setWorkflowId(workflowId);
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setProductSku(productSku);
        entity.setProductName(productName);
        entity.setTargetMarket(targetMarket);
        entity.setStatus(status);
        workflowRepository.save(entity);
    }

    @Transactional
    public void updateWorkflowStatus(String workflowId, String status, String errorMessage) {
        workflowRepository.findById(workflowId).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setErrorMessage(errorMessage);
            workflowRepository.save(entity);
        });
    }

    @Transactional
    public void saveTaskExecution(String taskId, String workflowId, String tenantId,
                                   AgentTask task, AgentResult result) {
        TaskExecutionEntity entity = new TaskExecutionEntity();
        entity.setTaskId(taskId);
        entity.setWorkflowId(workflowId);
        entity.setTenantId(tenantId);
        entity.setAgentType(task.agentType());
        entity.setStatus(task.status().name());
        entity.setInstruction(task.instruction());
        entity.setOutput(result != null ? result.output() : null);
        entity.setLatencyMs(result != null ? result.latencyMs() : null);
        taskExecutionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TaskExecutionEntity> findTasksByWorkflow(String workflowId) {
        return taskExecutionRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }

    @Transactional
    public void persistResult(String workflowId, String tenantId, DagScheduler.DagExecutionResult result) {
        updateWorkflowStatus(workflowId, result.allSuccess() ? "COMPLETED" : "FAILED", null);
        for (AgentTask task : result.executedTasks()) {
            AgentResult r = result.results().get(task.taskId());
            saveTaskExecution(task.taskId(), workflowId, tenantId, task, r);
        }
    }
}
