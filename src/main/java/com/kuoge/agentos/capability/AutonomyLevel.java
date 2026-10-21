package com.kuoge.agentos.capability;

/**
 * 策略自治档位（方案 2.3 六轴之一）。
 */
public enum AutonomyLevel {

    /** 只出建议卡片，人确认后执行 */
    SUGGEST_ONLY,
    /** 人确认后执行 */
    CONFIRM_THEN_ACT,
    /** 低风险自动执行，高风险仍确认 */
    AUTO_LOW_RISK
}
