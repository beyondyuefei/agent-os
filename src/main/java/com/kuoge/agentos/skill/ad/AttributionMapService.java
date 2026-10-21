package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.ArrayList;
import java.util.List;

/**
 * ad.attribution.map 归因映射（方案 3.3 ③，确定性服务，写观察快照）。
 * 规则：
 * <ul>
 *   <li>入口前置商品与实际购买商品分开（A 广告买 B：记组合成交 + B 购买事实，不写 A 销量）</li>
 *   <li>平台口径与站内口径分开</li>
 *   <li>订单关联不明保持 unknown，不强行分配</li>
 * </ul>
 * 输出 Observation 快照（当前状态 + 证据指针），供 PreReasoning 注入模型上下文。
 */
public class AttributionMapService implements DomainService<String, AttributionMapService.Observation> {

    /** Observation 快照：结构化状态 + 证据指针（Reducer 模式，模型只读快照） */
    public record Observation(String deliveryItemId,
                              long spendCents, long impressions, long clicks,
                              int attributedOrders,
                              int crossProductOrders,
                              int unknownOrders,
                              List<String> evidenceRefs,
                              List<String> gaps) {
        public Observation {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }
    }

    private final DeliveryItemRepository repository;
    private final AdFactStore factStore;

    public AttributionMapService(DeliveryItemRepository repository, AdFactStore factStore) {
        this.repository = repository;
        this.factStore = factStore;
    }

    @Override
    public Observation execute(SkillContext ctx, String deliveryItemId) {
        DeliveryItem item = repository.findById(deliveryItemId)
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + deliveryItemId));
        String entryProductId = item.getProductId();
        List<InsightReport> facts = item.getPlatformAdId() == null ? List.of()
                : factStore.platformFacts(item.getPlatformAdId());
        long spend = 0, impressions = 0, clicks = 0;
        int attributed = 0, crossProduct = 0, unknown = 0;
        List<String> evidence = new ArrayList<>();
        for (InsightReport fact : facts) {
            spend += fact.spendCents();
            impressions += fact.impressions();
            clicks += fact.clicks();
            evidence.add("ad_fact_platform:" + fact.platformAdId() + "@" + fact.window());
            for (InsightReport.AttributedOrder order : fact.orders()) {
                if (order.productId() == null || order.productId().isBlank()) {
                    // 关联不明保持 unknown，不强行分配
                    factStore.saveUnknownOrder(order);
                    unknown++;
                } else if (!order.productId().equals(entryProductId)) {
                    // A 广告买 B：记组合成交 + B 购买事实，不写 A 销量
                    factStore.saveAttribution(new AdFactStore.AttributionRecord(
                            order.orderId(), entryProductId, order.productId(), order.amountCents()));
                    crossProduct++;
                } else {
                    attributed++;
                }
            }
        }
        return new Observation(deliveryItemId, spend, impressions, clicks,
                attributed, crossProduct, unknown, evidence, factStore.gaps());
    }
}
