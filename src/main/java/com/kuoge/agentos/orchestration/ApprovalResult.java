package com.kuoge.agentos.orchestration;

public record ApprovalResult(
        boolean approved,
        String comment,
        String approvedBy
) {
}
