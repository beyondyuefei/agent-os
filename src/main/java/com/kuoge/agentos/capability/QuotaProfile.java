package com.kuoge.agentos.capability;

/**
 * 额度档位（方案 2.1 quota_profile）。
 */
public record QuotaProfile(
        String code,
        int dailyLlmCalls,
        int dailyActions,
        String modelTier
) {}
