package com.kuoge.agentos.business.service.crossborder;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossBorder 真实后台 API 集成测试。
 *
 * <p>需要设置 CrossBorder_EMAIL 与 CrossBorder_PASSWORD 环境变量才会执行。
 */
@EnabledIfEnvironmentVariable(named = "CrossBorder_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CrossBorder_PASSWORD", matches = ".+")
class CrossBorderApiClientIntegrationTest {

    @Test
    void shouldLoginAndCallBusinessApis() {
        CrossBorderProperties properties = new CrossBorderProperties();
        properties.setEmail(System.getenv("CrossBorder_EMAIL"));
        properties.setPassword(System.getenv("CrossBorder_PASSWORD"));
        properties.setTenantId(System.getenv().getOrDefault("CrossBorder_TENANT_ID", "1"));

        CrossBorderApiClient client = new CrossBorderApiClient(properties);

        // 商品 SKU 查询
        JsonNode skus = client.get("/api/product/skus", java.util.Map.of("keyword", "9926695059349504", "tenant_id", properties.getTenantId()));
        assertNotNull(skus);
        assertTrue(skus.has("list"));
        assertTrue(skus.path("list").isArray());

        // 后台类目树
        JsonNode categories = client.get("/api/categories/tree", java.util.Map.of("tenant_id", properties.getTenantId()));
        assertNotNull(categories);
        assertTrue(categories.isArray());

        // 优惠券列表
        JsonNode coupons = client.get("/api/marketing/coupons", java.util.Map.of("page", "1", "pageSize", "5"));
        assertNotNull(coupons);
        assertTrue(coupons.has("list"));

        // 营销活动列表
        JsonNode activities = client.get("/api/marketing/activities", java.util.Map.of("page", "1", "pageSize", "5"));
        assertNotNull(activities);
        assertTrue(activities.has("list"));

        // 日报汇总
        JsonNode summary = client.get("/api/data/dashboard/self-site/report/daily/summary",
                java.util.Map.of("dateFrom", "2026-07-20", "dateTo", "2026-07-27"));
        assertNotNull(summary);
        assertTrue(summary.has("items"));

        // 订单分页查询
        JsonNode orders = client.post("/api/orderAdmin/pageQueryOrders",
                java.util.Map.of("pageNo", 1, "pageSize", 5));
        assertNotNull(orders);
        assertTrue(orders.has("resultList"));
    }
}
