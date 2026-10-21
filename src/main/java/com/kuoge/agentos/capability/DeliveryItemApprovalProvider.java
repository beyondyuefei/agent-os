package com.kuoge.agentos.capability;

/**
 * 投放项批准状态提供方（由 delivery 模块实现，网关不直接依赖投放项存储）。
 */
public interface DeliveryItemApprovalProvider {

    /**
     * @return true = 投放项已批准且批准版本为最新
     */
    boolean approvedAndLatest(String tenantId, String deliveryItemId);
}
