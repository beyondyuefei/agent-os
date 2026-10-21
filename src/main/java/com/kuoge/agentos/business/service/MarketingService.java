package com.kuoge.agentos.business.service;

import com.kuoge.agentos.domain.GrowthPlan;

import java.util.List;

/**
 * 营销服务 SPI —— 对接内容中心 / 广告投放平台。
 */
public interface MarketingService {

    String generateListingTitle(String tenantId, String sku);

    List<String> generateKeywords(String tenantId, String sku);

    String generateBlogTopic(String tenantId, String sku);

    GrowthPlan createGrowthPlan(String tenantId, String sku);
}
