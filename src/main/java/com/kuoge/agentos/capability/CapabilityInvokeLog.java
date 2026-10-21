package com.kuoge.agentos.capability;

import java.time.LocalDateTime;

/**
 * 调用留痕（方案 2.1 capability_invoke_log）：
 * 额度扣减 + 拒绝/降级原因 + 版本快照。
 */
public record CapabilityInvokeLog(
        String tenantId,
        String capabilityCode,
        String action,
        Decision decision,
        String rejectCode,
        Integer planVersion,
        Integer policyVersion,
        String skillVersion,
        String degradeNote,
        LocalDateTime createdAt
) {}
