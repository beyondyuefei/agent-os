package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据回流与归因测试（验收规则 7：A 广告买 B；关联不明保持 unknown）。
 */
class AttributionMapServiceTest {

    private MockAdChannelAdapter channel;
    private DeliveryItemRepository repository;
    private AdFactStore factStore;
    private DataIngestService ingest;
    private AttributionMapService attribution;
    private String tenant = "t1";

    @BeforeEach
    void setUp() {
        channel = new MockAdChannelAdapter();
        repository = new DeliveryItemRepository();
        factStore = new AdFactStore();
        ingest = new DataIngestService(channel, factStore);
        attribution = new AttributionMapService(repository, factStore);
    }

    /** 验收规则 7：A 广告买 B → 成交记 B，不写 A 销量 */
    @Test
    void rule7_crossProductOrder_attributedToPurchasedProduct() {
        DeliveryItem item = new DeliveryItem("di_1", tenant, "img", "product_A", "plan", "col",
                DeliveryItemStatus.RUNNING);
        // 平台侧必须先存在该广告对象：通过 mock 创建，取回真实生成的 platformAdId
        String adId = channel.createAd(new com.kuoge.agentos.delivery.ApprovedItemSnapshot(
                "s1", "di_1", 1, 1, 1, "1.x", "product_A", "img", "link",
                java.util.List.of(), 100, "RESERVED", java.util.List.of())).platformAdId();
        item.setPlatformAdId(adId);
        repository.save(item);

        // 注入跨品订单：A 广告买 B + 一笔正常订单 + 一笔关联不明
        channel.injectOrder(adId, "order_1", "product_B", 5_000);
        channel.injectOrder(adId, "order_2", "product_A", 3_000);
        channel.injectOrder(adId, "order_3", null, 2_000);

        AdMapping mapping = new AdMapping("map_1", item.getDeliveryItemId(), tenant,
                adId, "as_1", "camp_1");
        ingest.execute(SkillContext.of(tenant),
                new DataIngestService.Input(mapping, new Window(LocalDate.now().minusDays(1), LocalDate.now())));

        AttributionMapService.Observation obs = attribution.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        assertEquals(1, obs.crossProductOrders(), "A 广告买 B 记组合成交");
        assertEquals(1, obs.attributedOrders(), "正常订单记 A");
        assertEquals(1, obs.unknownOrders(), "关联不明保持 unknown");
        // 组合成交记 B 购买事实，不写 A 销量
        AdFactStore.AttributionRecord record = factStore.attributionRecords().get(0);
        assertEquals("product_A", record.entryProductId());
        assertEquals("product_B", record.purchasedProductId());
        assertEquals(5_000, record.amountCents());
        // 证据指针存在
        assertFalse(obs.evidenceRefs().isEmpty());
    }

    /** 零成交缺口分类标记 */
    @Test
    void zeroOrder_gapMarked() {
        DeliveryItem item = new DeliveryItem("di_2", tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.RUNNING);
        String adId = channel.createAd(new com.kuoge.agentos.delivery.ApprovedItemSnapshot(
                "s2", "di_2", 1, 1, 1, "1.x", "p", "img", "link",
                java.util.List.of(), 100, "RESERVED", java.util.List.of())).platformAdId();
        repository.save(item);
        AdMapping mapping = new AdMapping("map_2", item.getDeliveryItemId(), tenant,
                adId, "as", "camp");
        ingest.execute(SkillContext.of(tenant),
                new DataIngestService.Input(mapping, new Window(LocalDate.now(), LocalDate.now())));
        assertTrue(factStore.gaps().stream().anyMatch(g -> g.contains("零成交")));
    }
}
