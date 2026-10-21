package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.ProductService;
import com.kuoge.agentos.domain.PlatformType;
import com.kuoge.agentos.domain.Product;
import com.kuoge.agentos.domain.Sku;
import java.util.List;

/**
 * Mock 实现（保留供非 Spring 场景或本地调试使用，未注册为 Bean）。
 */
public class MockProductService implements ProductService {

    @Override
    public Product getProduct(String tenantId, String sku) {
        return new Product(
                sku,
                "Sample Product " + sku,
                "Shoes",
                "Comfortable walking shoes",
                List.of("https://img.example.com/1.jpg"),
                List.of(new Sku(sku, sku + "-US-08", 20.0, 49.9, 1000, 0.8, "standard")),
                PlatformType.STANDALONE_STORE,
                "US",
                List.of("CE", "FDA")
        );
    }

    @Override
    public boolean verifyCertification(String tenantId, String sku) {
        return true;
    }

    @Override
    public boolean checkCategoryCompliance(String tenantId, String sku) {
        return true;
    }
}
