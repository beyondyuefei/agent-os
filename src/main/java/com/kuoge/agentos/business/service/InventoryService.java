package com.kuoge.agentos.business.service;

/**
 * 库存服务 SPI —— 对接 WMS / 库存中心。
 */
public interface InventoryService {

    int getStock(String tenantId, String sku);

    String forecastDemand(String tenantId, String sku);
}
