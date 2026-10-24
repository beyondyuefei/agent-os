package com.kuoge.agentos.channel;

import com.kuoge.agentos.delivery.ApprovedItemSnapshot;

/**
 * 广告渠道适配器（方案第五章）：Meta 为一期实例，接口可插拔。
 * Mock 与真实实现共用本契约，由契约测试保证可切换。
 */
public interface AdChannelAdapter {

    /** 支持什么、粒度多细、黑盒度 */
    ChannelCapabilityProfile profile();

    /** 按批准快照创建广告，返回外部 ID + 请求 ID */
    AdCreateReceipt createAd(ApprovedItemSnapshot snapshot);

    /** 幂等反查：创建状态未知时确认原请求是否已创建 */
    CreationStatus getCreationStatus(String platformRequestId);

    /** 反查确认已创建时取回平台广告 ID；通道不回传时返回 null */
    default String resolvePlatformAdId(String platformRequestId) {
        return null;
    }

    /** 启用（风险动作，停止类独立权限项之外） */
    void enable(AdMapping mapping);

    /** 暂停（停止类动作，独立权限项） */
    void pause(AdMapping mapping);

    /** 结束（停止类动作，独立权限项） */
    void end(AdMapping mapping);

    /** 拉取平台观察数据（数据回流） */
    InsightReport fetchInsights(AdMapping mapping, Window window);
}
