package com.kuoge.agentos.domain;

public record MarketplaceTask(
        String taskId,
        String taskType,
        String status,
        String assigneeAgent,
        String title,
        String description
) {
}
