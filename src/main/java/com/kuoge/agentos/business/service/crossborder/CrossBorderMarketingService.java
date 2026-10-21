package com.kuoge.agentos.business.service.crossborder;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.business.service.MarketingService;
import com.kuoge.agentos.domain.GrowthPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对接 CrossBorder 营销中心（优惠券、活动）的 {@link MarketingService} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderMarketingService implements MarketingService {

    private final CrossBorderApiClient apiClient;
    private final CrossBorderProperties properties;

    @Override
    public String generateListingTitle(String tenantId, String sku) {
        try {
            String effectiveTenantId = properties.resolveTenantId(tenantId);
            JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", effectiveTenantId));
            JsonNode list = data == null ? null : data.path("list");
            if (list != null && list.isArray() && !list.isEmpty()) {
                return list.get(0).path("title").asText("Premium Product");
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for sku={}", sku, e);
        }
        return "Premium Product - " + sku;
    }

    @Override
    public List<String> generateKeywords(String tenantId, String sku) {
        String effectiveTenantId = properties.resolveTenantId(tenantId);
        List<String> keywords = new ArrayList<>();
        try {
            JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", effectiveTenantId));
            JsonNode list = data == null ? null : data.path("list");
            if (list != null && list.isArray() && !list.isEmpty()) {
                String title = list.get(0).path("title").asText("");
                keywords.addAll(List.of(title.split("[^a-zA-Z0-9]+")));
            }

            JsonNode couponData = apiClient.get("/api/marketing/coupons", Map.of("page", "1", "pageSize", "5"));
            JsonNode coupons = couponData == null ? null : couponData.path("list");
            if (coupons != null && coupons.isArray()) {
                coupons.forEach(n -> keywords.add(n.path("name").asText()));
            }

            JsonNode activityData = apiClient.get("/api/marketing/activities", Map.of("page", "1", "pageSize", "5"));
            JsonNode activities = activityData == null ? null : activityData.path("list");
            if (activities != null && activities.isArray()) {
                activities.forEach(n -> keywords.add(n.path("activity_name").asText()));
            }
        } catch (Exception e) {
            log.warn("Failed to generate keywords for sku={}", sku, e);
        }
        keywords.removeIf(String::isBlank);
        if (keywords.isEmpty()) {
            return List.of("comfortable shoes", "walking shoes", "orthopedic loafers", "work shoes");
        }
        return keywords.stream().distinct().limit(10).toList();
    }

    @Override
    public String generateBlogTopic(String tenantId, String sku) {
        String title = generateListingTitle(tenantId, sku);
        return "Why " + title + " Is the Best Choice for Your Customers";
    }

    @Override
    public GrowthPlan createGrowthPlan(String tenantId, String sku) {
        List<String> actions = new ArrayList<>();
        try {
            JsonNode couponData = apiClient.get("/api/marketing/coupons", Map.of("page", "1", "pageSize", "5", "status", "1"));
            JsonNode coupons = couponData == null ? null : couponData.path("list");
            if (coupons != null && coupons.isArray()) {
                coupons.forEach(n -> actions.add("Apply coupon: " + n.path("name").asText()));
            }

            JsonNode activityData = apiClient.get("/api/marketing/activities", Map.of("page", "1", "pageSize", "5", "enabled", "1"));
            JsonNode activities = activityData == null ? null : activityData.path("list");
            if (activities != null && activities.isArray()) {
                activities.forEach(n -> actions.add("Join activity: " + n.path("activity_name").asText()));
            }
        } catch (Exception e) {
            log.warn("Failed to load marketing campaigns for growth plan sku={}", sku, e);
        }
        actions.addAll(List.of("Optimize listing title", "Publish SEO blog", "Generate UGC reviews", "Build email sequence"));
        return new GrowthPlan(
                sku,
                "Natural Traffic Growth Plan (CrossBorder)",
                actions.stream().distinct().limit(6).toList(),
                "Expected 36x organic traffic growth in 30 days",
                true
        );
    }
}
