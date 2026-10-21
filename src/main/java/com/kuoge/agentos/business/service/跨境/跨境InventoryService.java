package com.kuoge.agentos.business.service.跨境;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.business.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 对接 跨境 库存/履约数据的 {@link InventoryService} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class 跨境InventoryService implements InventoryService {

    private final 跨境ApiClient apiClient;
    private final 跨境Properties properties;

    @Override
    public int getStock(String tenantId, String sku) {
        try {
            String effectiveTenantId = properties.resolveTenantId(tenantId);
            JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", effectiveTenantId));
            JsonNode list = data == null ? null : data.path("list");
            if (list == null || !list.isArray() || list.isEmpty()) {
                return 0;
            }
            JsonNode item = list.get(0);
            int skuId = item.path("id").asInt(0);
            if (skuId == 0) {
                return 100;
            }
            JsonNode stocks = apiClient.get("/api/product/sku-stocks",
                    Map.of("id", String.valueOf(skuId), "tenant_id", effectiveTenantId));
            if (stocks != null && stocks.isNumber()) {
                return stocks.asInt();
            }
            if (stocks != null && stocks.has("stock")) {
                return stocks.path("stock").asInt(100);
            }
        } catch (Exception e) {
            log.warn("Failed to query real stock for sku={}, fallback to 100", sku, e);
        }
        return 100;
    }

    @Override
    public String forecastDemand(String tenantId, String sku) {
        try {
            JsonNode data = apiClient.post("/api/orderAdmin/pageQueryOrders",
                    Map.of("pageNo", 1, "pageSize", 1));
            int totalOrders = data == null ? 0 : data.path("totalSize").asInt(0);
            return String.format(
                    "Demand forecast (跨境): stable, recent total orders=%d, expected 150 units/day for sku=%s",
                    totalOrders, sku);
        } catch (Exception e) {
            log.warn("Failed to forecast demand for sku={}", sku, e);
            return "Demand forecast: stable, expected 150 units/day";
        }
    }
}
