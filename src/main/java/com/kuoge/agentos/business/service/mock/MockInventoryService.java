package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.InventoryService;

/**
 * Mock 实现（保留供非 Spring 场景或本地调试使用，未注册为 Bean）。
 */
public class MockInventoryService implements InventoryService {

    @Override
    public int getStock(String tenantId, String sku) {
        return 1000;
    }

    @Override
    public String forecastDemand(String tenantId, String sku) {
        return "Demand forecast: stable, expected 150 units/day";
    }
}
