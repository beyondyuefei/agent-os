package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用留痕（方案 2.1 capability_invoke_log）：额度扣减 + 拒绝/降级原因 + 版本快照。
 */
@Entity
@Table(name = "capability_invoke_log", indexes = @Index(name = "idx_invoke_log_tenant", columnList = "tenant_id"))
@Data
public class CapabilityInvokeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "capability_code", length = 64, nullable = false)
    private String capabilityCode;

    @Column(length = 64)
    private String action;

    /** Decision 枚举名：ALLOW / DEGRADED / REJECTED */
    @Column(length = 16, nullable = false)
    private String decision;

    @Column(name = "reject_code", length = 32)
    private String rejectCode;

    @Column(name = "plan_version")
    private Integer planVersion;

    @Column(name = "policy_version")
    private Integer policyVersion;

    @Column(name = "skill_version", length = 32)
    private String skillVersion;

    @Column(name = "degrade_note", length = 512)
    private String degradeNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
