package com.kuoge.agentos.domain;

import java.util.List;

public record GrowthPlan(
        String productId,
        String planName,
        List<String> actions,
        String expectedOutcome,
        boolean requiresApproval
) {
}
