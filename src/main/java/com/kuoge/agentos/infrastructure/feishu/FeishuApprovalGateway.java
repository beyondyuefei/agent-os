package com.kuoge.agentos.infrastructure.feishu;

import com.kuoge.agentos.infrastructure.feishu.FeishuApprovalClient.FeishuApprovalStatus;
import com.kuoge.agentos.orchestration.ApprovalResult;
import com.kuoge.agentos.orchestration.HumanApprovalGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书审批网关实现。
 *
 * <p>在 Mock 模式下自动通过，便于开发测试；生产环境会调用飞书开放平台 API
 * 创建审批实例并轮询状态，同时支持飞书审批回调 webhook。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalGateway extends HumanApprovalGateway {

    private final FeishuApprovalClient feishuClient;
    private final FeishuApprovalProperties properties;

    /** taskId -> 等待审批的未来结果 */
    private final Map<String, CompletableFuture<ApprovalResult>> pending = new ConcurrentHashMap<>();

    /** instanceCode -> taskId 映射，用于 webhook 回调 */
    private final Map<String, String> instanceToTask = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ApprovalResult> requestApproval(String taskId, String taskDescription) {
        CompletableFuture<ApprovalResult> future = new CompletableFuture<>();
        pending.put(taskId, future);

        if (properties.isMockMode()) {
            log.info("[FeishuApprovalGateway] Mock mode: auto-approve task {} - {}", taskId, taskDescription);
            approve(taskId, "system", "Auto-approved in mock mode");
            return future;
        }

        try {
            String instanceCode = feishuClient.createApprovalInstance(taskId, taskDescription);
            instanceToTask.put(instanceCode, taskId);
            log.info("[FeishuApprovalGateway] Created feishu approval instance {} for task {}", instanceCode, taskId);
            startPolling(taskId, instanceCode);
        } catch (Exception e) {
            log.error("[FeishuApprovalGateway] Failed to create approval for task {}", taskId, e);
            future.complete(new ApprovalResult(false, "Failed to create approval: " + e.getMessage(), "system"));
            pending.remove(taskId);
        }

        return future;
    }

    @Override
    public void approve(String taskId, String approvedBy, String comment) {
        CompletableFuture<ApprovalResult> future = pending.remove(taskId);
        if (future != null) {
            future.complete(new ApprovalResult(true, comment, approvedBy));
            log.info("[FeishuApprovalGateway] Task {} approved by {}", taskId, approvedBy);
        }
    }

    @Override
    public void reject(String taskId, String rejectedBy, String comment) {
        CompletableFuture<ApprovalResult> future = pending.remove(taskId);
        if (future != null) {
            future.complete(new ApprovalResult(false, comment, rejectedBy));
            log.info("[FeishuApprovalGateway] Task {} rejected by {}", taskId, rejectedBy);
        }
    }

    /**
     * 接收飞书审批状态变更回调。
     */
    public void handleWebhook(String instanceCode, FeishuApprovalStatus status, String approver) {
        String taskId = instanceToTask.get(instanceCode);
        if (taskId == null) {
            log.warn("[FeishuApprovalGateway] Received webhook for unknown instance {}", instanceCode);
            return;
        }

        switch (status) {
            case APPROVED -> approve(taskId, approver, "Approved via Feishu");
            case REJECTED -> reject(taskId, approver, "Rejected via Feishu");
            case CANCELED -> reject(taskId, approver, "Canceled via Feishu");
            default -> log.debug("[FeishuApprovalGateway] Instance {} status: {}", instanceCode, status);
        }
    }

    private void startPolling(String taskId, String instanceCode) {
        Thread poller = new Thread(() -> {
            try {
                for (int i = 0; i < properties.getMaxPollTimes(); i++) {
                    Thread.sleep(properties.getPollIntervalMs());
                    FeishuApprovalStatus status = feishuClient.queryApprovalStatus(instanceCode);
                    if (status == FeishuApprovalStatus.APPROVED) {
                        approve(taskId, "feishu-user", "Approved via Feishu polling");
                        return;
                    }
                    if (status == FeishuApprovalStatus.REJECTED || status == FeishuApprovalStatus.CANCELED) {
                        reject(taskId, "feishu-user", status.name().toLowerCase() + " via Feishu polling");
                        return;
                    }
                }
                reject(taskId, "system", "Approval timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reject(taskId, "system", "Polling interrupted");
            }
        });
        poller.setDaemon(true);
        poller.start();
    }
}
