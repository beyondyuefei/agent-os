package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 能力目录（方案 2.1 capability_def） */
@Entity
@Table(name = "capability_def")
@Data
public class CapabilityDefEntity {

    @Id
    @Column(length = 64)
    private String code;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "biz_line", length = 64)
    private String bizLine;

    /** ACTIVE / DEPRECATED */
    @Column(length = 16, nullable = false)
    private String status;
}
