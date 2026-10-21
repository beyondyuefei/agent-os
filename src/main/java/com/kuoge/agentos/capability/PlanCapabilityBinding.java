package com.kuoge.agentos.capability;

/**
 * 套餐 × 能力绑定（方案 2.1 plan_capability_binding）。
 * 单一事实来源：运行时由绑定把能力解析到唯一确定的 skill 版本。
 */
public record PlanCapabilityBinding(
        String planCode,
        String capabilityCode,
        boolean enabled,
        String policyProfileCode,
        int policyProfileVersion,
        String skillPackageCode,
        /** 版本线锁定，如 1.x(pro) / 2.x(ka) */
        String skillVersion,
        String quotaProfileCode
) {}
