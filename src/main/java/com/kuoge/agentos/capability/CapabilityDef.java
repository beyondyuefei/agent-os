package com.kuoge.agentos.capability;

import java.time.LocalDateTime;

/**
 * 能力目录条目（方案 2.1 capability_def）。
 */
public record CapabilityDef(
        String code,
        String name,
        String bizLine,
        String status          // ACTIVE / DEPRECATED
) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
