package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.time.LocalDate;
import java.util.List;

/**
 * ad.data.ingest 数据回流（方案 3.3 ③，确定性服务，ETL）。
 * 双源拉取落表（ad_fact_platform / ad_fact_onsite 按来源分区、不叠加），
 * 缺口按未测 / 零成交 / 未回传分类标记。
 * 频率差异：cron 直接读 policy（PRO 每日 / KA 每小时），同一 Skill 代码、行为由策略配置驱动。
 */
public class DataIngestService implements DomainService<DataIngestService.Input, InsightReport> {

    public record Input(AdMapping mapping, Window window) {}

    private final AdChannelAdapter channelAdapter;
    private final AdFactStore factStore;

    public DataIngestService(AdChannelAdapter channelAdapter, AdFactStore factStore) {
        this.channelAdapter = channelAdapter;
        this.factStore = factStore;
    }

    @Override
    public InsightReport execute(SkillContext ctx, Input input) {
        InsightReport report = channelAdapter.fetchInsights(input.mapping(), input.window());
        factStore.savePlatformFact(report);
        // 缺口分类标记
        if (report.impressions() == 0) {
            factStore.markGap(input.mapping().deliveryItemId() + ": 未测");
        } else if (report.orders().isEmpty()) {
            factStore.markGap(input.mapping().deliveryItemId() + ": 零成交");
        }
        return report;
    }
}
