package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.infrastructure.persistence.ads.ApprovedItemSnapshotJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 投放项表（方案 2.1 delivery_item）：批准快照整体 JSON 存 approved_snapshot 列。
 */
@Entity
@Table(name = "delivery_item", indexes = @Index(name = "idx_delivery_item_tenant", columnList = "tenant_id"))
@Data
public class DeliveryItemEntity {

    @Id
    @Column(name = "delivery_item_id", length = 64)
    private String deliveryItemId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "image_id", length = 64)
    private String imageId;

    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "collection_id", length = 64)
    private String collectionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private DeliveryItemStatus status;

    @Column(nullable = false)
    private int version;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    /** 申报预算（分） */
    @Column(name = "budget_amount", nullable = false)
    private long budgetAmount;

    /** 批准快照 JSON（方案 2.4），随修订升版失效 */
    @Lob
    @Convert(converter = ApprovedItemSnapshotJsonConverter.class)
    @Column(name = "approved_snapshot")
    private ApprovedItemSnapshot approvedSnapshot;

    @Column(name = "platform_request_id", length = 128)
    private String platformRequestId;

    @Column(name = "platform_ad_id", length = 128)
    private String platformAdId;
}
