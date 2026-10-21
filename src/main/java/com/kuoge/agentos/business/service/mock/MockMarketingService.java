package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.MarketingService;
import com.kuoge.agentos.domain.GrowthPlan;

import java.util.List;

/**
 * Mock 实现（保留供非 Spring 场景或本地调试使用，未注册为 Bean）。
 */
public class MockMarketingService implements MarketingService {

    @Override
    public String generateListingTitle(String tenantId, String sku) {
        return "Premium Orthopedic Loafers - All-Day Comfort for Walking & Work";
    }

    @Override
    public List<String> generateKeywords(String tenantId, String sku) {
        return List.of("comfortable shoes", "walking shoes", "orthopedic loafers", "work shoes");
    }

    @Override
    public String generateBlogTopic(String tenantId, String sku) {
        return "Why Orthopedic Loafers Are the Best Choice for Long Workdays";
    }

    @Override
    public GrowthPlan createGrowthPlan(String tenantId, String sku) {
        return new GrowthPlan(
                sku,
                "Natural Traffic Growth Plan",
                List.of("Optimize listing title", "Publish SEO blog", "Generate UGC reviews", "Build email sequence"),
                "Expected 36x organic traffic growth in 30 days",
                true
        );
    }
}
