package com.kuoge.agentos.infrastructure.persistence.ads;

import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.AuditEntryEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.DeliveryItemEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AuditEntryRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.DeliveryItemJpaRepository;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 投放项 JPA 存储（方案 2.1 delivery_item + audit_entry 独立审计路径）。
 */
public class JpaDeliveryItemStore implements DeliveryItemStore {

    private final DeliveryItemJpaRepository itemRepository;
    private final AuditEntryRepository auditRepository;

    public JpaDeliveryItemStore(DeliveryItemJpaRepository itemRepository,
                                AuditEntryRepository auditRepository) {
        this.itemRepository = itemRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public DeliveryItem save(DeliveryItem item) {
        itemRepository.save(toEntity(item));
        return item;
    }

    @Override
    public Optional<DeliveryItem> findById(String deliveryItemId) {
        return itemRepository.findById(deliveryItemId).map(JpaDeliveryItemStore::toDomain);
    }

    @Override
    public List<DeliveryItem> findByTenant(String tenantId) {
        return itemRepository.findByTenantId(tenantId).stream()
                .map(JpaDeliveryItemStore::toDomain)
                .toList();
    }

    /** 独立审计路径（停止类动作全量留痕，落 audit_entry 表） */
    @Override
    public void appendAudit(String entry) {
        AuditEntryEntity entity = new AuditEntryEntity();
        entity.setEntry(entry);
        entity.setCreatedAt(Instant.now());
        auditRepository.save(entity);
    }

    @Override
    public List<String> auditTrail() {
        return auditRepository.findAll(Sort.by("id")).stream()
                .map(AuditEntryEntity::getEntry)
                .toList();
    }

    private static DeliveryItemEntity toEntity(DeliveryItem item) {
        DeliveryItemEntity entity = new DeliveryItemEntity();
        entity.setDeliveryItemId(item.getDeliveryItemId());
        entity.setTenantId(item.getTenantId());
        entity.setImageId(item.getImageId());
        entity.setProductId(item.getProductId());
        entity.setPlanId(item.getPlanId());
        entity.setCollectionId(item.getCollectionId());
        entity.setStatus(item.getStatus());
        entity.setVersion(item.getVersion());
        entity.setRejectReason(item.getRejectReason());
        entity.setBudgetAmount(item.getBudgetAmount());
        entity.setApprovedSnapshot(item.getApprovedSnapshot());
        entity.setPlatformRequestId(item.getPlatformRequestId());
        entity.setPlatformAdId(item.getPlatformAdId());
        return entity;
    }

    private static DeliveryItem toDomain(DeliveryItemEntity entity) {
        return DeliveryItem.restore(entity.getDeliveryItemId(), entity.getTenantId(),
                entity.getImageId(), entity.getProductId(), entity.getPlanId(),
                entity.getCollectionId(), entity.getStatus(), entity.getVersion(),
                entity.getRejectReason(), entity.getBudgetAmount(), entity.getApprovedSnapshot(),
                entity.getPlatformRequestId(), entity.getPlatformAdId());
    }
}
