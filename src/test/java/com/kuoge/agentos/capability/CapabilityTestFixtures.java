package com.kuoge.agentos.capability;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试装配：套餐 / 绑定 / 策略档位的标准测试数据。
 * PRO 档（SUGGEST_ONLY）与 KA 档（AUTO_LOW_RISK）覆盖六轴差异。
 */
public final class CapabilityTestFixtures {

    public static final String TENANT_PRO = "tenant_pro";
    public static final String TENANT_KA = "tenant_ka";

    private CapabilityTestFixtures() {}

    /** 初始化能力目录 + PRO/KA 两个套餐与绑定 */
    public static CapabilityStore storeWithProAndKa() {
        CapabilityStore store = new InMemoryCapabilityStore();
        populate(store);
        return store;
    }

    /** 向既有 store 填充标准测试数据（Spring 上下文测试复用同一装配） */
    public static void populate(CapabilityStore store) {
        for (CapabilityCode c : CapabilityCode.values()) {
            store.saveCapability(new CapabilityDef(c.code(), c.name(), "投流", "ACTIVE"));
        }
        // 策略档位（方案 2.3：同一能力两个档位，差异落在 policy_profile 版本上）
        store.savePolicy(new PolicyProfile("EVOLVE_PRO", 3, AutonomyLevel.SUGGEST_ONLY,
                "0 2 * * *",
                Map.of("ctr_low", 0.02, "click_no_convert_hours", 48),
                Map.of("primary", "qwen-turbo", "fallback", "qwen-plus"),
                List.of("ad_fact_platform", "ad_fact_onsite")));
        store.savePolicy(new PolicyProfile("EVOLVE_KA", 5, AutonomyLevel.AUTO_LOW_RISK,
                "0 * * * *",
                Map.of("ctr_low", 0.02, "spend_no_order_ratio", 1.5, "min_impressions", 800),
                Map.of("primary", "qwen-max", "fallback", "qwen-plus"),
                List.of("ad_fact_platform", "ad_fact_onsite", "product_margin", "inventory", "refund")));
        store.savePolicy(new PolicyProfile("OPS_STD", 1, AutonomyLevel.CONFIRM_THEN_ACT,
                null, Map.of(), Map.of(), List.of("ad_fact_platform")));

        // 套餐
        store.savePlan(new PlanDef("PLAN_PRO", "进阶版", 1, "ACTIVE"));
        store.savePlan(new PlanDef("PLAN_KA", "KA 版", 1, "ACTIVE"));

        LocalDateTime now = LocalDateTime.now();
        store.saveTenantPlan(new TenantPlan(TENANT_PRO, "PLAN_PRO", 1,
                now.minusDays(1), now.plusDays(30), false));
        store.saveTenantPlan(new TenantPlan(TENANT_KA, "PLAN_KA", 1,
                now.minusDays(1), now.plusDays(30), false));

        // 套餐 × 能力绑定：evolve 按 PRO/KA 锁不同 skill 版本线；投放操作两档都有
        for (String plan : List.of("PLAN_PRO", "PLAN_KA")) {
            store.saveBinding(new PlanCapabilityBinding(plan, CapabilityCode.AD_DELIVERY_OPS.code(),
                    true, "OPS_STD", 1, "ad-ops", "1.x", "QUOTA_STD"));
        }
        store.saveBinding(new PlanCapabilityBinding("PLAN_PRO", CapabilityCode.AD_EFFECT_EVOLVE.code(),
                true, "EVOLVE_PRO", 3, "ad-evolve-suggest", "1.x", "QUOTA_STD"));
        store.saveBinding(new PlanCapabilityBinding("PLAN_KA", CapabilityCode.AD_EFFECT_EVOLVE.code(),
                true, "EVOLVE_KA", 5, "ad-evolve-suggest", "2.x", "QUOTA_KA"));
        // 素材生成能力仅 KA 开通（验收规则 1：PRO 触发 → ENT_001）
        store.saveBinding(new PlanCapabilityBinding("PLAN_KA", CapabilityCode.AD_CREATIVE_GENERATE.code(),
                true, "OPS_STD", 1, "ad-creative", "1.x", "QUOTA_KA"));

        store.saveQuota(new QuotaProfile("QUOTA_STD", 100, 50, "standard"));
        store.saveQuota(new QuotaProfile("QUOTA_KA", 1000, 500, "premium"));
    }

    /** 为套餐补 ad.data.observe 绑定与带 cron 的策略（数据回流触发链测试用） */
    public static void bindDataObserve(CapabilityStore store, String planCode, String cronExpr) {
        String policyCode = "INGEST_" + planCode;
        store.savePolicy(new PolicyProfile(policyCode, 1, AutonomyLevel.CONFIRM_THEN_ACT,
                cronExpr, Map.of(), Map.of(), List.of("ad_fact_platform")));
        store.saveBinding(new PlanCapabilityBinding(planCode, CapabilityCode.AD_DATA_OBSERVE.code(),
                true, policyCode, 1, "ad-ingest", "1.x", "QUOTA_STD"));
    }

    public static RevocationNotifier noopNotifier() {
        return (tenantId, reason) -> { };
    }
}
