package com.kuoge.agentos.business.service;

import com.kuoge.agentos.domain.Product;

/**
 * 商品服务 SPI —— 对接 ERP / 商品中心。
 */
public interface ProductService {

    Product getProduct(String tenantId, String sku);

    boolean verifyCertification(String tenantId, String sku);

    boolean checkCategoryCompliance(String tenantId, String sku);
}
