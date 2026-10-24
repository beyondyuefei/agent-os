package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/** 关联不明订单表（unknown_order）：保持 unknown 不强行分配，AttributedOrder 整体 JSON */
@Entity
@Table(name = "unknown_order")
@Data
public class UnknownOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(name = "order_json", nullable = false)
    private String orderJson;
}
