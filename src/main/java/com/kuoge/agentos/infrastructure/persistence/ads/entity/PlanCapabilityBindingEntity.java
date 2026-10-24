package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 套餐 × 能力绑定（方案 2.1 plan_capability_binding）。
 * 主键格式：planCode#capabilityCode。
 */
@Entity
@Table(name = "plan_capability_binding")
@Data
public class PlanCapabilityBindingEntity {

    @Id
    @Column(length = 160)
    private String id;

    @Column(name = "plan_code", length = 64, nullable = false)
    private String planCode;

    @Column(name = "capability_code", length = 64, nullable = false)
    private String capabilityCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "policy_profile_code", length = 64)
    private String policyProfileCode;

    @Column(name = "policy_profile_version")
    private int policyProfileVersion;

    @Column(name = "skill_package_code", length = 64)
    private String skillPackageCode;

    /** 版本线锁定，如 1.x(pro) / 2.x(ka) */
    @Column(name = "skill_version", length = 32)
    private String skillVersion;

    @Column(name = "quota_profile_code", length = 64)
    private String quotaProfileCode;

    public static String buildId(String planCode, String capabilityCode) {
        return planCode + "#" + capabilityCode;
    }
}
