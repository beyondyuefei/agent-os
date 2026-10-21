package com.kuoge.agentos.business.service;

/**
 * 数据分析服务 SPI —— 对接 BI / 数据中心。
 */
public interface AnalyticsService {

    double queryGmv(String tenantId, String sku, int days);

    double queryTraffic(String tenantId, String sku, int days);

    double queryConversionRate(String tenantId, String sku, int days);
}
