package com.kuoge.agentos.business.service.mock;

import com.kuoge.agentos.business.service.ListingService;
import org.springframework.stereotype.Service;

/**
 * 刊登服务 Mock 实现（当前作为默认 Bean 注册，便于开发测试）。
 */
@Service
public class MockListingService implements ListingService {

    @Override
    public String publishListing(String tenantId, String targetMarket, String sku) {
        return targetMarket.toUpperCase() + "-LISTING-" + sku.replaceAll("[^A-Za-z0-9]", "-") + "-" + System.currentTimeMillis();
    }

    @Override
    public boolean syncImages(String tenantId, String sku) {
        return true;
    }

    @Override
    public boolean updateInventory(String tenantId, String sku, int quantity) {
        return true;
    }
}
