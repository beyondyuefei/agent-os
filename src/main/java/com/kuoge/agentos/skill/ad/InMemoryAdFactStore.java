package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.InsightReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 广告事实与经验内存存储（供单测直接 new 使用）。
 */
public class InMemoryAdFactStore implements AdFactStore {

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

    @Override
    public void savePlatformFact(InsightReport report) {
        platformFacts.computeIfAbsent(report.platformAdId(), k -> new ArrayList<>()).add(report);
    }

    @Override
    public List<InsightReport> platformFacts(String platformAdId) {
        return List.copyOf(platformFacts.getOrDefault(platformAdId, List.of()));
    }

    @Override
    public void saveAttribution(AttributionRecord record) {
        attributionRecords.add(record);
    }

    @Override
    public List<AttributionRecord> attributionRecords() {
        return List.copyOf(attributionRecords);
    }

    @Override
    public void saveUnknownOrder(InsightReport.AttributedOrder order) {
        unknownOrders.add(order);
    }

    @Override
    public List<InsightReport.AttributedOrder> unknownOrders() {
        return List.copyOf(unknownOrders);
    }

    @Override
    public void markGap(String gap) {
        gaps.add(gap);
    }

    @Override
    public List<String> gaps() {
        return List.copyOf(gaps);
    }

    @Override
    public void saveExperience(Experience experience) {
        experiences.add(experience);
    }

    /**
     * 读取生效经验：仅已审核可见；尝试读取未审核规则将被拒绝（验收规则 9）。
     */
    @Override
    public List<Experience> effectiveExperiences() {
        List<Experience> result = new ArrayList<>();
        for (Experience e : experiences) {
            if (e.approved()) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public List<Experience> allExperiences() {
        return List.copyOf(experiences);
    }
}
