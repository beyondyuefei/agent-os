package com.kuoge.agentos.channel;

import java.util.List;

/**
 * 平台观察数据（方案 8.6 Insight.query 的平台侧部分）。
 *
 * @param orders 平台归因订单列表；(productId, amount) 供归因拆分用
 */
public record InsightReport(
        String platformAdId,
        Window window,
        long spendCents,
        long impressions,
        long clicks,
        List<AttributedOrder> orders
) {

    /** 平台归因订单：orderId + 实际购买商品 + 金额 */
    public record AttributedOrder(String orderId, String productId, long amountCents) {}

    public InsightReport {
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
