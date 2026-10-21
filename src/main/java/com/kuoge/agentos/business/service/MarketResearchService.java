package com.kuoge.agentos.business.service;

import java.util.List;
import java.util.Map;

/**
 * 市场研究服务 SPI —— 对接选品工具 / 趋势数据 / 竞品分析。
 */
public interface MarketResearchService {

    /**
     * 搜索目标市场趋势关键词/类目。
     */
    List<String> searchTrends(String tenantId, String targetMarket);

    /**
     * 分析指定关键词/类目的竞争强度。
     */
    Map<String, Object> analyzeCompetition(String tenantId, String targetMarket, String keyword);

    /**
     * 对候选 SKU 进行潜力评分。
     *
     * @return 0-100 的评分
     */
    int scoreProductPotential(String tenantId, String targetMarket, String sku);
}
