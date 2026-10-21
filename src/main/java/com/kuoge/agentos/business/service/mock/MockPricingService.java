package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.PricingService;
import com.kuoge.agentos.domain.ProfitReport;

/**
 * Mock 实现（保留供非 Spring 场景或本地调试使用，未注册为 Bean）。
 */
public class MockPricingService implements PricingService {

    @Override
    public double getProductCost(String tenantId, String sku) {
        return 20.0;
    }

    @Override
    public double getLogisticsCost(String tenantId, String sku) {
        return 5.0;
    }

    @Override
    public double getPlatformCommissionRate(String tenantId, String sku) {
        return 0.15;
    }

    @Override
    public ProfitReport calculateProfit(String tenantId, String sku, double sellingPrice) {
        double productCost = getProductCost(tenantId, sku);
        double logisticsCost = getLogisticsCost(tenantId, sku);
        double commission = sellingPrice * getPlatformCommissionRate(tenantId, sku);
        double tax = sellingPrice * 0.05;
        double grossProfit = sellingPrice - productCost - logisticsCost - commission - tax;
        double margin = grossProfit / sellingPrice;
        return new ProfitReport(
                sku, sellingPrice, productCost, logisticsCost, commission, tax,
                grossProfit, margin, margin > 0.2,
                margin > 0.2 ? "Profitable, recommend launch" : "Low margin, review pricing"
        );
    }
}
