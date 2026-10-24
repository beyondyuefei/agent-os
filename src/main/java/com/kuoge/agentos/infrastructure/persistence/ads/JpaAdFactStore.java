package com.kuoge.agentos.infrastructure.persistence.ads;

import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.AdFactPlatformEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.AttributionRecordEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.ExperienceEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.GapTagEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.UnknownOrderEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AdFactPlatformRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AttributionRecordRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.ExperienceRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.GapTagRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.UnknownOrderRepository;
import com.kuoge.agentos.skill.ad.AdFactStore;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * 广告事实与经验 JPA 存储：
 * ad_fact_platform（InsightReport JSON + platformAdId 索引）/ attribution_record /
 * unknown_order（JSON）/ experience（字段展开）/ gap_tag。
 */
public class JpaAdFactStore implements AdFactStore {

    private final AdFactPlatformRepository platformRepository;
    private final AttributionRecordRepository attributionRepository;
    private final UnknownOrderRepository unknownOrderRepository;
    private final ExperienceRepository experienceRepository;
    private final GapTagRepository gapTagRepository;

    public JpaAdFactStore(AdFactPlatformRepository platformRepository,
                          AttributionRecordRepository attributionRepository,
                          UnknownOrderRepository unknownOrderRepository,
                          ExperienceRepository experienceRepository,
                          GapTagRepository gapTagRepository) {
        this.platformRepository = platformRepository;
        this.attributionRepository = attributionRepository;
        this.unknownOrderRepository = unknownOrderRepository;
        this.experienceRepository = experienceRepository;
        this.gapTagRepository = gapTagRepository;
    }

    @Override
    public void savePlatformFact(InsightReport report) {
        AdFactPlatformEntity entity = new AdFactPlatformEntity();
        entity.setPlatformAdId(report.platformAdId());
        entity.setReportJson(AdsJsonSupport.toJson(report));
        platformRepository.save(entity);
    }

    @Override
    public List<InsightReport> platformFacts(String platformAdId) {
        return platformRepository.findByPlatformAdIdOrderById(platformAdId).stream()
                .map(e -> AdsJsonSupport.fromJson(e.getReportJson(), InsightReport.class))
                .toList();
    }

    @Override
    public void saveAttribution(AttributionRecord record) {
        AttributionRecordEntity entity = new AttributionRecordEntity();
        entity.setOrderId(record.orderId());
        entity.setEntryProductId(record.entryProductId());
        entity.setPurchasedProductId(record.purchasedProductId());
        entity.setAmountCents(record.amountCents());
        attributionRepository.save(entity);
    }

    @Override
    public List<AttributionRecord> attributionRecords() {
        return attributionRepository.findAll(Sort.by("id")).stream()
                .map(e -> new AttributionRecord(e.getOrderId(), e.getEntryProductId(),
                        e.getPurchasedProductId(), e.getAmountCents()))
                .toList();
    }

    /** 关联不明保持 unknown，不强行分配 */
    @Override
    public void saveUnknownOrder(InsightReport.AttributedOrder order) {
        UnknownOrderEntity entity = new UnknownOrderEntity();
        entity.setOrderJson(AdsJsonSupport.toJson(order));
        unknownOrderRepository.save(entity);
    }

    @Override
    public List<InsightReport.AttributedOrder> unknownOrders() {
        return unknownOrderRepository.findAll(Sort.by("id")).stream()
                .map(e -> AdsJsonSupport.fromJson(e.getOrderJson(), InsightReport.AttributedOrder.class))
                .toList();
    }

    /** 数据缺口标记：未测 / 零成交 / 未回传 */
    @Override
    public void markGap(String gap) {
        GapTagEntity entity = new GapTagEntity();
        entity.setTag(gap);
        gapTagRepository.save(entity);
    }

    @Override
    public List<String> gaps() {
        return gapTagRepository.findAll(Sort.by("id")).stream().map(GapTagEntity::getTag).toList();
    }

    @Override
    public void saveExperience(Experience experience) {
        ExperienceEntity entity = new ExperienceEntity();
        entity.setExpId(experience.id());
        entity.setRule(experience.rule());
        entity.setSource(experience.source());
        entity.setApplicableCondition(experience.applicableCondition());
        entity.setApproved(experience.approved());
        experienceRepository.save(entity);
    }

    /**
     * 读取生效经验：仅已审核可见；尝试读取未审核规则将被拒绝（验收规则 9）。
     */
    @Override
    public List<Experience> effectiveExperiences() {
        return experienceRepository.findByApprovedTrueOrderById().stream()
                .map(JpaAdFactStore::toExperience)
                .toList();
    }

    @Override
    public List<Experience> allExperiences() {
        return experienceRepository.findAll(Sort.by("id")).stream()
                .map(JpaAdFactStore::toExperience)
                .toList();
    }

    private static Experience toExperience(ExperienceEntity e) {
        return new Experience(e.getExpId(), e.getRule(), e.getSource(),
                e.getApplicableCondition(), e.isApproved());
    }
}
