package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 预算授权（方案 2.1 budget_authorization；mock 语义：无记录默认有效） */
@Entity
@Table(name = "budget_authorization")
@Data
public class BudgetAuthorizationEntity {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "valid_flag", nullable = false)
    private boolean valid;
}
