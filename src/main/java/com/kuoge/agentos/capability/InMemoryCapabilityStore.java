package com.kuoge.agentos.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 能力域内存存储（供单测直接 new 使用；结构对齐方案 2.1 DDL）。
 */
public class InMemoryCapabilityStore implements CapabilityStore {

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
    @Override
    public void saveCapability(CapabilityDef def) {
        capabilities.put(def.code(), def);
    }

    @Override
    public Optional<CapabilityDef> findCapability(String code) {
        return Optional.ofNullable(capabilities.get(code));
    }

    // ---- plan_def ----
    @Override
    public void savePlan(PlanDef plan) {
        plans.put(plan.planCode(), plan);
    }

    @Override
    public Optional<PlanDef> findPlan(String planCode) {
        return Optional.ofNullable(plans.get(planCode));
    }

    // ---- tenant_plan ----
    @Override
    public void saveTenantPlan(TenantPlan tenantPlan) {
        tenantPlans.put(tenantPlan.tenantId(), tenantPlan);
    }

    @Override
    public Optional<TenantPlan> findTenantPlan(String tenantId) {
        return Optional.ofNullable(tenantPlans.get(tenantId));
    }

    /** 全部租户套餐（cron 触发链遍历用） */
    @Override
    public List<TenantPlan> listTenantPlans() {
        return List.copyOf(tenantPlans.values());
    }

    // ---- plan_capability_binding ----
    @Override
    public void saveBinding(PlanCapabilityBinding binding) {
        bindings.put(binding.planCode() + "#" + binding.capabilityCode(), binding);
    }

    @Override
    public Optional<PlanCapabilityBinding> findBinding(String planCode, String capabilityCode) {
        return Optional.ofNullable(bindings.get(planCode + "#" + capabilityCode));
    }

    // ---- policy_profile ----
    @Override
    public void savePolicy(PolicyProfile policy) {
        policies.put(policy.code() + "#" + policy.version(), policy);
    }

    @Override
    public Optional<PolicyProfile> findPolicy(String code, int version) {
        return Optional.ofNullable(policies.get(code + "#" + version));
    }

    // ---- quota_profile ----
    @Override
    public void saveQuota(QuotaProfile quota) {
        quotas.put(quota.code(), quota);
    }

    @Override
    public Optional<QuotaProfile> findQuota(String code) {
        return Optional.ofNullable(quotas.get(code));
    }

    // ---- capability_invoke_log ----
    @Override
    public void appendInvokeLog(CapabilityInvokeLog log) {
        invokeLogs.add(log);
    }

    @Override
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
    @Override
    public void setBudgetAuthorization(String tenantId, boolean valid) {
        budgetAuthorization.put(tenantId, valid);
    }

    @Override
    public boolean budgetAuthorizationValid(String tenantId) {
        return budgetAuthorization.getOrDefault(tenantId, true);
    }
}
