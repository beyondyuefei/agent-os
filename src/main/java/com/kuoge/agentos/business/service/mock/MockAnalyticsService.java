package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.AnalyticsService;

/**
 * Mock 实现（保留供非 Spring 场景或本地调试使用，未注册为 Bean）。
 */
public class MockAnalyticsService implements AnalyticsService {

    @Override
    public double queryGmv(String tenantId, String sku, int days) {
        return 11444.0;
    }

    @Override
    public double queryTraffic(String tenantId, String sku, int days) {
        return 1847.0;
    }

    @Override
    public double queryConversionRate(String tenantId, String sku, int days) {
        return 0.035;
    }
}
