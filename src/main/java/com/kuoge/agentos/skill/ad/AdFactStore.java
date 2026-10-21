package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.InsightReport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 广告事实与经验内存存储（一期 mock；对应方案的
 * ad_fact_platform / ad_fact_onsite 双表 + Observation 快照 + 经验候选库）。
 */
public class AdFactStore {

    /** 平台事实：platformAdId → 窗口 → 报告 */
    private final Map<String, List<InsightReport>> platformFacts = new ConcurrentHashMap<>();
    /** 归因后的组合成交：entryProductId(入口前置商品) → purchasedProductId → 金额 */
    private final List<AttributionRecord> attributionRecords = new CopyOnWriteArrayList<>();
    /** 关联不明保持 unknown，不强行分配 */
    private final List<InsightReport.AttributedOrder> unknownOrders = new CopyOnWriteArrayList<>();
    /** 经验候选库 */
    private final List<Experience> experiences = new CopyOnWriteArrayList<>();
    /** 数据缺口标记：未测 / 零成交 / 未回传 */
    private final List<String> gaps = new CopyOnWriteArrayList<>();

    /** 归因记录：A 广告买 B → 记组合成交 + B 购买事实，不写 A 销量 */
    public record AttributionRecord(String orderId, String entryProductId,
                                    String purchasedProductId, long amountCents) {}

    /** 经验条目：未审核规则不可被任何 Skill 读为生效配置（验收规则 9） */
    public record Experience(String id, String rule, String source,
                             String applicableCondition, boolean approved) {}

    public void savePlatformFact(InsightReport report) {
        platformFacts.computeIfAbsent(report.platformAdId(), k -> new ArrayList<>()).add(report);
    }

    public List<InsightReport> platformFacts(String platformAdId) {
        return List.copyOf(platformFacts.getOrDefault(platformAdId, List.of()));
    }

    public void saveAttribution(AttributionRecord record) {
        attributionRecords.add(record);
    }

    public List<AttributionRecord> attributionRecords() {
        return List.copyOf(attributionRecords);
    }

    public void saveUnknownOrder(InsightReport.AttributedOrder order) {
        unknownOrders.add(order);
    }

    public List<InsightReport.AttributedOrder> unknownOrders() {
        return List.copyOf(unknownOrders);
    }

    public void markGap(String gap) {
        gaps.add(gap);
    }

    public List<String> gaps() {
        return List.copyOf(gaps);
    }

    public void saveExperience(Experience experience) {
        experiences.add(experience);
    }

    /**
     * 读取生效经验：仅已审核可见；尝试读取未审核规则将被拒绝（验收规则 9）。
     */
    public List<Experience> effectiveExperiences() {
        List<Experience> result = new ArrayList<>();
        for (Experience e : experiences) {
            if (e.approved()) {
                result.add(e);
            }
        }
        return result;
    }

    public List<Experience> allExperiences() {
        return List.copyOf(experiences);
    }
}
