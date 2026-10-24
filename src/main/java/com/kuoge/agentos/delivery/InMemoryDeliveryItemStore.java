package com.kuoge.agentos.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投放项内存存储（一期 mock CRUD，供单测直接 new 使用）。
 */
public class InMemoryDeliveryItemStore implements DeliveryItemStore {

    private final Map<String, DeliveryItem> items = new ConcurrentHashMap<>();
    /** 审计留痕：tenantId → 审计条目 */
    private final List<String> auditTrail = new ArrayList<>();

    @Override
    public synchronized DeliveryItem save(DeliveryItem item) {
        items.put(item.getDeliveryItemId(), item);
        return item;
    }

    @Override
    public Optional<DeliveryItem> findById(String deliveryItemId) {
        return Optional.ofNullable(items.get(deliveryItemId));
    }

    @Override
    public List<DeliveryItem> findByTenant(String tenantId) {
        List<DeliveryItem> result = new ArrayList<>();
        for (DeliveryItem item : items.values()) {
            if (item.getTenantId().equals(tenantId)) {
                result.add(item);
            }
        }
        return result;
    }

    /** 独立审计路径（停止类动作全量留痕） */
    @Override
    public synchronized void appendAudit(String entry) {
        auditTrail.add(entry);
    }

    @Override
    public List<String> auditTrail() {
        return List.copyOf(auditTrail);
    }
}
