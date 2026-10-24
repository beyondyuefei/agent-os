package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * 审计留痕表（audit_entry）：停止类动作独立审计路径，全量留痕。
 */
@Entity
@Table(name = "audit_entry")
@Data
public class AuditEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false)
    private String entry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
