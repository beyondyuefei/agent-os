package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 套餐定义（方案 2.1 plan_def） */
@Entity
@Table(name = "plan_def")
@Data
public class PlanDefEntity {

    @Id
    @Column(name = "plan_code", length = 64)
    private String planCode;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(length = 16, nullable = false)
    private String status;
}
