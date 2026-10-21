package com.kuoge.agentos.orchestration.kanban;

import com.kuoge.agentos.orchestration.TaskStatus;

import java.time.Instant;

/**
 * Kanban 看板卡片 —— 对应 Hermes Agent Kanban 中的一个任务项。
 */
public record KanbanCard(
        String cardId,
        String workflowId,
        String taskId,
        String agentType,
        String title,
        TaskStatus status,
        String assignedTo,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String resultSummary
) {

    public KanbanCard {
        cardId = cardId == null ? "" : cardId;
        workflowId = workflowId == null ? "" : workflowId;
        taskId = taskId == null ? "" : taskId;
        agentType = agentType == null ? "" : agentType;
        title = title == null ? "" : title;
        status = status == null ? TaskStatus.PENDING : status;
        assignedTo = assignedTo == null ? "" : assignedTo;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static KanbanCard create(String workflowId, String taskId, String agentType, String title) {
        return new KanbanCard(
                workflowId + "-" + taskId,
                workflowId,
                taskId,
                agentType,
                title,
                TaskStatus.PENDING,
                agentType,
                Instant.now(),
                null,
                null,
                ""
        );
    }

    public KanbanCard withStatus(TaskStatus newStatus) {
        Instant now = Instant.now();
        Instant started = this.startedAt;
        Instant completed = this.completedAt;
        if (newStatus == TaskStatus.RUNNING && started == null) {
            started = now;
        }
        if ((newStatus == TaskStatus.COMPLETED || newStatus == TaskStatus.FAILED) && completed == null) {
            completed = now;
        }
        return new KanbanCard(cardId, workflowId, taskId, agentType, title,
                newStatus, assignedTo, createdAt, started, completed, resultSummary);
    }

    public KanbanCard withResultSummary(String summary) {
        return new KanbanCard(cardId, workflowId, taskId, agentType, title,
                status, assignedTo, createdAt, startedAt, completedAt, summary);
    }
}
