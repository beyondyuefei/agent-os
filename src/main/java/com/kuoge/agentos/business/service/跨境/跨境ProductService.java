package com.kuoge.agentos.business.service.跨境;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.business.service.ProductService;
import com.kuoge.agentos.domain.PlatformType;
import com.kuoge.agentos.domain.Product;
import com.kuoge.agentos.domain.Sku;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对接 跨境 真实商品中心的 {@link ProductService} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class 跨境ProductService implements ProductService {

    private final 跨境ApiClient apiClient;
    private final 跨境Properties properties;

    @Override
    public Product getProduct(String tenantId, String sku) {
        String effectiveTenantId = properties.resolveTenantId(tenantId);
        JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", effectiveTenantId));
        JsonNode list = data == null ? null : data.path("list");
        if (list == null || !list.isArray() || list.isEmpty()) {
            log.warn("跨境 product not found for sku={}", sku);
            throw new IllegalArgumentException("Product not found: " + sku);
        }
        JsonNode item = list.get(0);
        String skuCode = item.path("sku_code").asText(sku);
        String productCode = item.path("product_code").asText(sku);
        String title = item.path("title").asText("Unknown");
        double cost = item.path("cost").asDouble(0.0);
        double salePrice = item.path("sale_price").asDouble(item.path("price").asDouble(0.0));

        Sku domainSku = new Sku(skuCode, skuCode, cost, salePrice, 0, 0.0, "standard");
        return new Product(
                productCode,
                title,
                detectCategory(tenantId, item),
                title,
                List.of(),
                List.of(domainSku),
                PlatformType.STANDALONE_STORE,
                "US",
                List.of()
        );
    }

    @Override
    public boolean verifyCertification(String tenantId, String sku) {
        try {
            JsonNode item = findSku(properties.resolveTenantId(tenantId), sku);
            if (item == null) {
                return false;
            }
            // audit_status 为 1 表示已审核通过；null 或其他视为未录入，默认放行
            int auditStatus = item.path("audit_status").asInt(1);
            return auditStatus == 1;
        } catch (Exception e) {
            log.warn("verifyCertification failed for sku={}, fallback to true", sku, e);
            return true;
        }
    }

    @Override
    public boolean checkCategoryCompliance(String tenantId, String sku) {
        try {
            String effectiveTenantId = properties.resolveTenantId(tenantId);
            Product product = getProduct(effectiveTenantId, sku);
            String title = product.name().toLowerCase();
            List<String> leafNames = loadBackendLeafNames(effectiveTenantId);
            return leafNames.stream().anyMatch(title::contains);
        } catch (Exception e) {
            log.warn("checkCategoryCompliance failed for sku={}, fallback to true", sku, e);
            return true;
        }
    }

    private JsonNode findSku(String tenantId, String sku) {
        JsonNode data = apiClient.get("/api/product/skus", Map.of("keyword", sku, "tenant_id", tenantId));
        JsonNode list = data == null ? null : data.path("list");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    private String detectCategory(String tenantId, JsonNode item) {
        // 商品 SKU 接口未返回类目编码，尝试用商品标题匹配后台叶子类目
        String title = item.path("title").asText("").toLowerCase();
        for (String leaf : loadBackendLeafNames(tenantId)) {
            if (title.contains(leaf.toLowerCase())) {
                return leaf;
            }
        }
        return "Uncategorized";
    }

    private List<String> loadBackendLeafNames(String tenantId) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode tree = apiClient.get("/api/categories/tree", Map.of("tenant_id", tenantId));
            if (tree != null && tree.isArray()) {
                collectLeafNames(tree, names);
            }
        } catch (Exception e) {
            log.warn("Failed to load backend category tree", e);
        }
        return names;
    }

    private void collectLeafNames(JsonNode nodes, List<String> names) {
        for (JsonNode node : nodes) {
            JsonNode children = node.path("children");
            if (children.isMissingNode() || !children.isArray() || children.isEmpty()) {
                names.add(node.path("name").asText());
            } else {
                collectLeafNames(children, names);
            }
        }
    }
}
