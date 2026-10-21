package com.kuoge.agentos.skill;

/**
 * Skill 风险等级（方案 3.1 声明式字段）。
 * 网关在 PreToolCall 按风险等级决定放行、降级执行还是转人工确认。
 */
public enum RiskLevel {
    READ,
    WRITE_LOW_RISK,
    WRITE_HIGH_RISK
}
