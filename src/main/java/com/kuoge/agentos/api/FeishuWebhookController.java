package com.kuoge.agentos.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.infrastructure.feishu.FeishuApprovalClient.FeishuApprovalStatus;
import com.kuoge.agentos.infrastructure.feishu.FeishuApprovalGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 飞书审批回调接口。
 */
@Slf4j
@RestController
@RequestMapping("/webhook/feishu")
@RequiredArgsConstructor
public class FeishuWebhookController {

    private final FeishuApprovalGateway approvalGateway;

    @PostMapping("/approval")
    public ResponseEntity<String> onApprovalCallback(@RequestBody JsonNode payload) {
        log.info("[FeishuWebhook] Received approval callback: {}", payload);

        try {
            String instanceCode = payload.path("instance_code").asText();
            String statusCode = payload.path("status").asText("PENDING");
            String approver = payload.path("approver").path("open_id").asText("feishu-user");

            FeishuApprovalStatus status = FeishuApprovalStatus.fromCode(statusCode);
            approvalGateway.handleWebhook(instanceCode, status, approver);

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("[FeishuWebhook] Failed to process callback", e);
            return ResponseEntity.ok("ok");
        }
    }
}
