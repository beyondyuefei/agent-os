package com.kuoge.agentos.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 能力域内存存储（一期 mock 实现，替代方案 2.1 的 8 张表 CRUD；
 * 结构对齐 DDL，后续可平替为 JPA Repository）。
 */
public class CapabilityStore {

    private final Map<String, CapabilityDef> capabilities = new ConcurrentHashMap<>();
    private final Map<String, PlanDef> plans = new ConcurrentHashMap<>();
    private final Map<String, TenantPlan> tenantPlans = new ConcurrentHashMap<>();
    private final Map<String, PlanCapabilityBinding> bindings = new ConcurrentHashMap<>();
    private final Map<String, PolicyProfile> policies = new ConcurrentHashMap<>();
    private final Map<String, QuotaProfile> quotas = new ConcurrentHashMap<>();
    private final List<CapabilityInvokeLog> invokeLogs = new CopyOnWriteArrayList<>();
    /** 预算授权是否有效（mock：真实场景来自预算台账服务） */
    private final Map<String, Boolean> budgetAuthorization = new ConcurrentHashMap<>();

    // ---- capability_def ----
    public void saveCapability(CapabilityDef def) {
        capabilities.put(def.code(), def);
    }

    public Optional<CapabilityDef> findCapability(String code) {
        return Optional.ofNullable(capabilities.get(code));
    }

    // ---- plan_def ----
    public void savePlan(PlanDef plan) {
        plans.put(plan.planCode(), plan);
    }

    public Optional<PlanDef> findPlan(String planCode) {
        return Optional.ofNullable(plans.get(planCode));
    }

    // ---- tenant_plan ----
    public void saveTenantPlan(TenantPlan tenantPlan) {
        tenantPlans.put(tenantPlan.tenantId(), tenantPlan);
    }

    public Optional<TenantPlan> findTenantPlan(String tenantId) {
        return Optional.ofNullable(tenantPlans.get(tenantId));
    }

    // ---- plan_capability_binding ----
    public void saveBinding(PlanCapabilityBinding binding) {
        bindings.put(binding.planCode() + "#" + binding.capabilityCode(), binding);
    }

    public Optional<PlanCapabilityBinding> findBinding(String planCode, String capabilityCode) {
        return Optional.ofNullable(bindings.get(planCode + "#" + capabilityCode));
    }

    // ---- policy_profile ----
    public void savePolicy(PolicyProfile policy) {
        policies.put(policy.code() + "#" + policy.version(), policy);
    }

    public Optional<PolicyProfile> findPolicy(String code, int version) {
        return Optional.ofNullable(policies.get(code + "#" + version));
    }

    // ---- quota_profile ----
    public void saveQuota(QuotaProfile quota) {
        quotas.put(quota.code(), quota);
    }

    public Optional<QuotaProfile> findQuota(String code) {
        return Optional.ofNullable(quotas.get(code));
    }

    // ---- capability_invoke_log ----
    public void appendInvokeLog(CapabilityInvokeLog log) {
        invokeLogs.add(log);
    }

    public List<CapabilityInvokeLog> listInvokeLogs(String tenantId) {
        List<CapabilityInvokeLog> result = new ArrayList<>();
        for (CapabilityInvokeLog log : invokeLogs) {
            if (log.tenantId().equals(tenantId)) {
                result.add(log);
            }
        }
        return result;
    }

    // ---- budget authorization (mock) ----
    public void setBudgetAuthorization(String tenantId, boolean valid) {
        budgetAuthorization.put(tenantId, valid);
    }

    public boolean budgetAuthorizationValid(String tenantId) {
        return budgetAuthorization.getOrDefault(tenantId, true);
    }
}
