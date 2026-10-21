package com.kuoge.agentos.business.service;

/**
 * 刊登服务 SPI —— 对接电商平台刊登接口。
 */
public interface ListingService {

    /**
     * 发布商品 Listing。
     *
     * @return 平台 Listing ID 或 URL
     */
    String publishListing(String tenantId, String targetMarket, String sku);

    /**
     * 同步商品图片。
     */
    boolean syncImages(String tenantId, String sku);

    /**
     * 更新平台库存。
     */
    boolean updateInventory(String tenantId, String sku, int quantity);
}
