package com.kuoge.agentos.delivery;

import java.util.List;
import java.util.Optional;

/**
 * 投放项存储接口（方案 2.1 delivery_item 表 CRUD + 独立审计路径）。
 * 实现：InMemoryDeliveryItemStore（单测）/ JpaDeliveryItemStore（Spring 上下文）。
 */
public interface DeliveryItemStore {

    DeliveryItem save(DeliveryItem item);

    Optional<DeliveryItem> findById(String deliveryItemId);

    List<DeliveryItem> findByTenant(String tenantId);

    /** 独立审计路径（停止类动作全量留痕） */
    void appendAudit(String entry);

    List<String> auditTrail();
}
