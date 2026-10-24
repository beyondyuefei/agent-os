package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 额度档位（方案 2.1 quota_profile） */
@Entity
@Table(name = "quota_profile")
@Data
public class QuotaProfileEntity {

    @Id
    @Column(length = 64)
    private String code;

    @Column(name = "daily_llm_calls", nullable = false)
    private int dailyLlmCalls;

    @Column(name = "daily_actions", nullable = false)
    private int dailyActions;

    @Column(name = "model_tier", length = 32)
    private String modelTier;
}
