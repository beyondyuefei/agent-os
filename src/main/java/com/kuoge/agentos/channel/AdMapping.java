package com.kuoge.agentos.channel;

import java.time.LocalDate;
import java.util.List;

/**
 * 广告映射：系统投放项 ↔ 平台对象（单图投放项 → Campaign/AdSet/Ad 结构在接入时确定，
 * 业务状态与回执仍按单图保留）。
 */
public record AdMapping(
        String adMappingId,
        String deliveryItemId,
        String tenantId,
        String platformAdId,
        String platformAdSetId,
        String platformCampaignId
) {}
