package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.InsightReport;

import java.util.List;

/**
 * 广告事实与经验存储接口（对应方案的
 * ad_fact_platform / ad_fact_onsite 双表 + Observation 快照 + 经验候选库）。
 *
 * <p>实现：InMemoryAdFactStore（单测）/ JpaAdFactStore（Spring 上下文）。
 */
public interface AdFactStore {

    /** 归因记录：A 广告买 B → 记组合成交 + B 购买事实，不写 A 销量 */
    record AttributionRecord(String orderId, String entryProductId,
                             String purchasedProductId, long amountCents) {}

    /** 经验条目：未审核规则不可被任何 Skill 读为生效配置（验收规则 9） */
    record Experience(String id, String rule, String source,
                      String applicableCondition, boolean approved) {}

    void savePlatformFact(InsightReport report);

    List<InsightReport> platformFacts(String platformAdId);

    void saveAttribution(AttributionRecord record);

    List<AttributionRecord> attributionRecords();

    /** 关联不明保持 unknown，不强行分配 */
    void saveUnknownOrder(InsightReport.AttributedOrder order);

    List<InsightReport.AttributedOrder> unknownOrders();

    /** 数据缺口标记：未测 / 零成交 / 未回传 */
    void markGap(String gap);

    List<String> gaps();

    void saveExperience(Experience experience);

    /**
     * 读取生效经验：仅已审核可见；尝试读取未审核规则将被拒绝（验收规则 9）。
     */
    List<Experience> effectiveExperiences();

    List<Experience> allExperiences();
}
