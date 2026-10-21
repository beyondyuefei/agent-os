package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.agent.BusinessAgent;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.orchestration.checkpoint.CheckpointEvaluator;
import com.kuoge.agentos.orchestration.checkpoint.ValidationResult;
import com.kuoge.agentos.orchestration.kanban.KanbanBoard;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * DAG 并行调度器 —— 集成检查点、Kanban 看板与重规划策略。
 */
@Slf4j
public class DagScheduler {

    private final AgentRegistry agentRegistry;
    private final HumanApprovalGateway approvalGateway;
    private final ExecutorService executor;
    private final ReplanPolicy replanPolicy;
    private final CheckpointEvaluator checkpointEvaluator;
    private final KanbanBoard kanbanBoard;
    private final ReplanPolicy.ReplanContext replanContext = new ReplanPolicy.ReplanContext();

    public DagScheduler(AgentRegistry agentRegistry, HumanApprovalGateway approvalGateway,
                        int parallelism, ReplanPolicy replanPolicy) {
        this(agentRegistry, approvalGateway, parallelism, replanPolicy, null, null);
    }

    public DagScheduler(AgentRegistry agentRegistry, HumanApprovalGateway approvalGateway,
                        int parallelism, ReplanPolicy replanPolicy,
                        CheckpointEvaluator checkpointEvaluator, KanbanBoard kanbanBoard) {
        this.agentRegistry = agentRegistry;
        this.approvalGateway = approvalGateway;
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.replanPolicy = replanPolicy;
        this.checkpointEvaluator = checkpointEvaluator;
        this.kanbanBoard = kanbanBoard;
    }

    public DagExecutionResult execute(ExecutionGraph graph, AgentContext baseContext) {
        Map<String, AgentResult> results = new ConcurrentHashMap<>();
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> failed = ConcurrentHashMap.newKeySet();
        List<AgentTask> executedTasks = Collections.synchronizedList(new ArrayList<>());

        while (!graph.isCompleted(completed) && failed.isEmpty()) {
            List<AgentTask> ready = graph.getReadyTasks(completed);
            if (ready.isEmpty() && !graph.isCompleted(completed)) {
                log.warn("Deadlock detected in execution graph");
                break;
            }

            List<Future<?>> futures = new ArrayList<>();
            for (AgentTask task : ready) {
                updateKanban(task.taskId(), TaskStatus.RUNNING);
                futures.add(executor.submit(() -> {
                    try {
                        AgentResult result = executeTask(task, baseContext);
                        results.put(task.taskId(), result);
                        executedTasks.add(task.withResult(result));

                        if (result.success()) {
                            ValidationResult checkpoint = evaluateCheckpoint(task, result);
                            switch (checkpoint.action()) {
                                case PASS -> {
                                    completed.add(task.taskId());
                                    updateKanban(task.taskId(), TaskStatus.COMPLETED, result.output());
                                }
                                case RETRY -> handleRetry(task, result, graph, executedTasks, failed);
                                case SKIP -> {
                                    log.warn("Checkpoint skipped for task {}", task.taskId());
                                    completed.add(task.taskId());
                                    updateKanban(task.taskId(), TaskStatus.COMPLETED, "skipped: " + checkpoint.message());
                                }
                                case STOP -> {
                                    failed.add(task.taskId());
                                    updateKanban(task.taskId(), TaskStatus.FAILED, checkpoint.message());
                                }
                            }
                        } else {
                            handleFailure(task, result, graph, executedTasks, failed);
                        }
                    } catch (Exception e) {
                        log.error("Task {} failed", task.taskId(), e);
                        failed.add(task.taskId());
                        executedTasks.add(task.failed(e.getMessage()));
                        updateKanban(task.taskId(), TaskStatus.FAILED, e.getMessage());
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Waiting for task completion failed", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        boolean allSuccess = failed.isEmpty() && graph.isCompleted(completed);
        return new DagExecutionResult(allSuccess, results, executedTasks);
    }

    private AgentResult executeTask(AgentTask task, AgentContext baseContext) {
        BusinessAgent agent = agentRegistry.resolve(task.agentType())
                .orElseThrow(() -> new IllegalStateException("Unknown agent type: " + task.agentType()));

        AgentContext context = new AgentContext(
                baseContext.tenantId(),
                baseContext.userId(),
                baseContext.sessionId(),
                task.taskId(),
                enrichInstruction(task, baseContext),
                baseContext.toolRegistry(),
                baseContext.metadata(),
                baseContext.sharedMemory()
        );

        if (task.requiresApproval()) {
            updateKanban(task.taskId(), TaskStatus.WAITING_APPROVAL);
            ApprovalResult approval = approvalGateway.requestApproval(task.taskId(), task.instruction()).join();
            if (!approval.approved()) {
                return new AgentResult(task.agentType(), false, null, 0,
                        "Rejected by human: " + approval.comment());
            }
        }

        return agent.execute(context);
    }

    private ValidationResult evaluateCheckpoint(AgentTask task, AgentResult result) {
        if (checkpointEvaluator == null || task.checkpointConfig().isEmpty()) {
            return ValidationResult.pass("No checkpoint");
        }
        return checkpointEvaluator.evaluate(task, result, task.checkpointConfig());
    }

    private void handleFailure(AgentTask task, AgentResult result, ExecutionGraph graph,
                               List<AgentTask> executedTasks, Set<String> failed) {
        replanContext.recordFailure(task.taskId());
        ReplanPolicy.ReplanDecision decision = replanPolicy.replan(graph, task, replanContext);

        switch (decision.action()) {
            case STOP -> {
                failed.add(task.taskId());
                updateKanban(task.taskId(), TaskStatus.FAILED, result.output());
            }
            case SKIP -> {
                log.warn("Skipping failed task {}", task.taskId());
                executedTasks.add(task.withStatus(TaskStatus.COMPLETED));
                updateKanban(task.taskId(), TaskStatus.COMPLETED, "skipped after failure");
            }
            case RETRY -> {
                AgentTask retryTask = decision.newTask();
                if (retryTask != null) {
                    log.info("Retrying task {} as {}", task.taskId(), retryTask.taskId());
                    graph.addTask(retryTask);
                    updateKanban(task.taskId(), TaskStatus.FAILED, "retrying as " + retryTask.taskId());
                } else {
                    failed.add(task.taskId());
                    updateKanban(task.taskId(), TaskStatus.FAILED, "retry task is null");
                }
            }
        }
    }

    private void handleRetry(AgentTask task, AgentResult result, ExecutionGraph graph,
                             List<AgentTask> executedTasks, Set<String> failed) {
        ReplanPolicy.ReplanDecision decision = replanPolicy.replan(graph, task, replanContext);
        if (decision.action() == ReplanPolicy.ReplanDecision.Action.RETRY && decision.newTask() != null) {
            log.info("Checkpoint retry for task {} as {}", task.taskId(), decision.newTask().taskId());
            graph.addTask(decision.newTask());
            updateKanban(task.taskId(), TaskStatus.FAILED, "checkpoint retry as " + decision.newTask().taskId());
        } else {
            failed.add(task.taskId());
            updateKanban(task.taskId(), TaskStatus.FAILED, "checkpoint failed: " + decision.reason());
        }
    }

    private String enrichInstruction(AgentTask task, AgentContext baseContext) {
        return task.instruction();
    }

    private void updateKanban(String taskId, TaskStatus status) {
        updateKanban(taskId, status, null);
    }

    private void updateKanban(String taskId, TaskStatus status, String resultSummary) {
        if (kanbanBoard == null) return;
        kanbanBoard.move(taskId, status);
        if (resultSummary != null && !resultSummary.isBlank()) {
            kanbanBoard.updateResult(taskId, resultSummary.length() > 200
                    ? resultSummary.substring(0, 200) + "..."
                    : resultSummary);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public record DagExecutionResult(
            boolean allSuccess,
            Map<String, AgentResult> results,
            List<AgentTask> executedTasks
    ) {}
}
