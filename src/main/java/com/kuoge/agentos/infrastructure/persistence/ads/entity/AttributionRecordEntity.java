package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 组合成归因记录表（attribution_record）：A 广告买 B → 记组合成交 + B 购买事实 */
@Entity
@Table(name = "attribution_record")
@Data
public class AttributionRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", length = 64, nullable = false)
    private String orderId;

    /** 入口前置商品 */
    @Column(name = "entry_product_id", length = 64, nullable = false)
    private String entryProductId;

    @Column(name = "purchased_product_id", length = 64, nullable = false)
    private String purchasedProductId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
}
