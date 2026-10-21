package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.MarketResearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 市场研究服务 Mock 实现（当前作为默认 Bean 注册，便于开发测试）。
 */
@Service
public class MockMarketResearchService implements MarketResearchService {

    @Override
    public List<String> searchTrends(String tenantId, String targetMarket) {
        return List.of("comfortable shoes", "walking shoes", "orthopedic loafers", "travel backpack");
    }

    @Override
    public Map<String, Object> analyzeCompetition(String tenantId, String targetMarket, String keyword) {
        return Map.of(
                "keyword", keyword,
                "competitionLevel", "medium",
                "avgPrice", 45.99,
                "opportunityScore", 72
        );
    }

    @Override
    public int scoreProductPotential(String tenantId, String targetMarket, String sku) {
        // 基于 SKU hash 生成稳定分数，便于测试
        return Math.max(50, Math.min(95, 70 + (sku.hashCode() % 25)));
    }
}
