package com.kuoge.agentos.capability;

import java.time.LocalDateTime;

/**
 * 租户套餐订阅（方案 2.1 tenant_plan）。
 */
public record TenantPlan(
        String tenantId,
        String planCode,
        int planVersion,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        boolean revoked
) {

    /** 套餐是否在有效期内且未被撤权 */
    public boolean effective(LocalDateTime now) {
        if (revoked) {
            return false;
        }
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || now.isBefore(effectiveTo);
    }
}
