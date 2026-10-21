package com.kuoge.agentos.orchestration.kanban;

import com.kuoge.agentos.orchestration.TaskStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Kanban 任务看板 —— 借鉴 Hermes Agent v0.12 的 Kanban multi-agent orchestration。
 *
 * <p>所有子 Agent 的任务以卡片形式在看板上流转，支持按状态查询、依赖追踪和并行可视化。
 */
public class KanbanBoard {

    private final String workflowId;
    private final Map<String, KanbanCard> cards = new ConcurrentHashMap<>();

    public KanbanBoard(String workflowId) {
        this.workflowId = workflowId == null ? "" : workflowId;
    }

    public KanbanCard addCard(String taskId, String agentType, String title) {
        KanbanCard card = KanbanCard.create(workflowId, taskId, agentType, title);
        cards.put(taskId, card);
        return card;
    }

    public KanbanCard move(String taskId, TaskStatus newStatus) {
        KanbanCard card = cards.get(taskId);
        if (card == null) {
            return null;
        }
        KanbanCard updated = card.withStatus(newStatus);
        cards.put(taskId, updated);
        return updated;
    }

    public KanbanCard updateResult(String taskId, String resultSummary) {
        KanbanCard card = cards.get(taskId);
        if (card == null) {
            return null;
        }
        KanbanCard updated = card.withResultSummary(resultSummary);
        cards.put(taskId, updated);
        return updated;
    }

    public Optional<KanbanCard> getCard(String taskId) {
        return Optional.ofNullable(cards.get(taskId));
    }

    public List<KanbanCard> getCardsByStatus(TaskStatus status) {
        return cards.values().stream()
                .filter(c -> c.status() == status)
                .collect(Collectors.toList());
    }

    public List<KanbanCard> allCards() {
        return List.copyOf(cards.values());
    }

    public boolean allDone() {
        return cards.values().stream().allMatch(c ->
                c.status() == TaskStatus.COMPLETED || c.status() == TaskStatus.FAILED);
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
