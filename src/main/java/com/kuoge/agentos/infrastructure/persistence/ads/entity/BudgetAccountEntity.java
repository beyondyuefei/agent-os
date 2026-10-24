package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 预算台账账户（方案 3.3 ① 五元组）：
 * 恒等式 已花 + 占用 + 预留 + 余额 = 授权总额 任意时刻成立。
 */
@Entity
@Table(name = "budget_account")
@Data
public class BudgetAccountEntity {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** 授权总额（分） */
    @Column(nullable = false)
    private long authorized;

    /** 预留 */
    @Column(nullable = false)
    private long reserved;

    /** 占用 */
    @Column(nullable = false)
    private long occupied;

    /** 已花 */
    @Column(nullable = false)
    private long spent;

    /** 对账差异挂账 */
    @Column(nullable = false)
    private long discrepancy;
}
