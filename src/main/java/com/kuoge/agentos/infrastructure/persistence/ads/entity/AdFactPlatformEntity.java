package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 平台事实表（方案 ad_fact_platform）：InsightReport 整体 JSON 存 report_json 列，
 * platform_ad_id 展开为索引列供查询。
 */
@Entity
@Table(name = "ad_fact_platform", indexes = @Index(name = "idx_ad_fact_platform_ad", columnList = "platform_ad_id"))
@Data
public class AdFactPlatformEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_ad_id", length = 128, nullable = false)
    private String platformAdId;

    /** InsightReport JSON（含窗口 / 消耗 / 曝光 / 点击 / 归因订单） */
    @Lob
    @Column(name = "report_json", nullable = false)
    private String reportJson;
}
