package com.kuoge.agentos.orchestration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工审批网关。
 */
public class HumanApprovalGateway {

    private final Map<String, CompletableFuture<ApprovalResult>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<ApprovalResult> requestApproval(String taskId, String taskDescription) {
        CompletableFuture<ApprovalResult> future = new CompletableFuture<>();
        pending.put(taskId, future);
        return future;
    }

    public void approve(String taskId, String approvedBy, String comment) {
        CompletableFuture<ApprovalResult> future = pending.remove(taskId);
        if (future != null) {
            future.complete(new ApprovalResult(true, comment, approvedBy));
        }
    }

    public void reject(String taskId, String rejectedBy, String comment) {
        CompletableFuture<ApprovalResult> future = pending.remove(taskId);
        if (future != null) {
            future.complete(new ApprovalResult(false, comment, rejectedBy));
        }
    }
}
