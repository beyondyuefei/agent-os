package com.kuoge.agentos.business.service.psylos1;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.business.service.PricingService;
import com.kuoge.agentos.domain.ProfitReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 对接 Psylos1 商品成本的 {@link PricingService} 实现。
 *
 * <p>物流、佣金等字段当前后台未暴露独立接口，保留合理默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Psylos1PricingService implements PricingService {

    private final Psylos1ApiClient apiClient;
    private final Psylos1Properties properties;

    @Override
    public double getProductCost(String tenantId, String sku) {
        JsonNode item = findSku(properties.resolveTenantId(tenantId), sku);
        if (item == null) {
            return 20.0;
        }
        return item.path("cost").asDouble(20.0);
    }

    @Override
    public double getLogisticsCost(String tenantId, String sku) {
        // 后台暂无物流成本接口，按售价比例估算
        return 5.0;
    }

    @Override
    public double getPlatformCommissionRate(String tenantId, String sku) {
        // 自建站默认佣金比例
        return 0.15;
    }

    @Override
    public ProfitReport calculateProfit(String tenantId, String sku, double sellingPrice) {
        double productCost = getProductCost(tenantId, sku);
        double logisticsCost = getLogisticsCost(tenantId, sku);
        double commission = sellingPrice * getPlatformCommissionRate(tenantId, sku);
        double tax = sellingPrice * 0.05;
        double grossProfit = sellingPrice - productCost - logisticsCost - commission - tax;
        double margin = sellingPrice > 0 ? grossProfit / sellingPrice : 0;
        return new ProfitReport(
                sku, sellingPrice, productCost, logisticsCost, commission, tax,
                grossProfit, margin, margin > 0.2,
                margin > 0.2 ? "Profitable, recommend launch" : "Low margin, review pricing"
        );
    }

    private JsonNode findSku(String tenantId, String sku) {
        try {
            JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", tenantId));
            JsonNode list = data == null ? null : data.path("list");
            if (list == null || !list.isArray() || list.isEmpty()) {
                return null;
            }
            return list.get(0);
        } catch (Exception e) {
            log.warn("Failed to query sku cost for sku={}", sku, e);
            return null;
        }
    }
}
