package com.kuoge.agentos.business.service;

import com.kuoge.agentos.domain.ProfitReport;

/**
 * 定价与利润服务 SPI —— 对接价格中心 / 财务系统。
 */
public interface PricingService {

    double getProductCost(String tenantId, String sku);

    double getLogisticsCost(String tenantId, String sku);

    double getPlatformCommissionRate(String tenantId, String sku);

    ProfitReport calculateProfit(String tenantId, String sku, double sellingPrice);
}
