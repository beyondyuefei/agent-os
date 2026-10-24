package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 策略档位（方案 2.1 policy_profile / 2.3 六轴）：
 * thresholds / modelRoute / dataScope 以 JSON 存 TEXT 列。
 * 主键格式：code#version。
 */
@Entity
@Table(name = "policy_profile")
@Data
public class PolicyProfileEntity {

    @Id
    @Column(length = 160)
    private String id;

    @Column(length = 64, nullable = false)
    private String code;

    @Column(nullable = false)
    private int version;

    /** 自治档位轴（AutonomyLevel 枚举名） */
    @Column(name = "autonomy_level", length = 32, nullable = false)
    private String autonomyLevel;

    /** 运行频率轴：cron 表达式 */
    @Column(name = "cron_expr", length = 64)
    private String cronExpr;

    /** 分析深度轴：阈值配置 JSON */
    @Lob
    @Column(name = "thresholds_json")
    private String thresholdsJson;

    /** 资源模型轴：模型档位与降级链 JSON */
    @Lob
    @Column(name = "model_route_json")
    private String modelRouteJson;

    /** 数据范围轴：可读数据清单 JSON */
    @Lob
    @Column(name = "data_scope_json")
    private String dataScopeJson;

    public static String buildId(String code, int version) {
        return code + "#" + version;
    }
}
