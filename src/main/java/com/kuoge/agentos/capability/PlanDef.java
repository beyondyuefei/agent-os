package com.kuoge.agentos.capability;

/**
 * 套餐定义（方案 2.1 plan_def）。
 */
public record PlanDef(
        String planCode,
        String name,
        int version,
        String status
) {}
