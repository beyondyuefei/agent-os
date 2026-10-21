package com.kuoge.agentos.channel;

import java.util.List;

/**
 * 渠道能力画像（方案 5）：支持什么、粒度多细、黑盒度。
 */
public record ChannelCapabilityProfile(
        String channel,
        /** 支持的对象层级，如 campaign / adset / ad */
        List<String> objectLevels,
        /** 平台侧自动优化是否可关闭、可审计（如 Advantage+ 类行为；关不掉则不接该套餐） */
        boolean autoOptimizationDisableable,
        /** 平台原生幂等能力是否已验证 */
        boolean nativeIdempotencyVerified
) {

    public ChannelCapabilityProfile {
        objectLevels = objectLevels == null ? List.of() : List.copyOf(objectLevels);
    }
}
