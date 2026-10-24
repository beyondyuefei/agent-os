package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 租户套餐订阅（方案 2.1 tenant_plan） */
@Entity
@Table(name = "tenant_plan")
@Data
public class TenantPlanEntity {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "plan_code", length = 64, nullable = false)
    private String planCode;

    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(nullable = false)
    private boolean revoked;
}
