package com.kuoge.agentos.skill.ad;

import java.util.List;

/**
 * 投前检查集 B 未通过（方案 8.4 错误码 PREFLIGHT_B_FAILED）。
 * 创建 / 启用 / 恢复前置阻断：消息携带未通过项 code 列表，不触达渠道。
 */
public class PreflightBFailedException extends RuntimeException {

    private final List<String> failedCodes;

    public PreflightBFailedException(List<String> failedCodes) {
        super("PREFLIGHT_B_FAILED: " + String.join(", ", failedCodes));
        this.failedCodes = List.copyOf(failedCodes);
    }

    public List<String> failedCodes() {
        return failedCodes;
    }
}
