package com.kuoge.agentos.capability;

import java.util.List;
import java.util.Optional;

/**
 * 能力域存储接口（方案 2.1 的 8 张表 CRUD）：
 * capability_def / plan_def / tenant_plan / plan_capability_binding /
 * policy_profile / quota_profile / capability_invoke_log / budget_authorization。
 *
 * <p>实现：InMemoryCapabilityStore（单测）/ JpaCapabilityStore（Spring 上下文）。
 */
public interface CapabilityStore {

    // ---- capability_def ----
    void saveCapability(CapabilityDef def);

    Optional<CapabilityDef> findCapability(String code);

    // ---- plan_def ----
    void savePlan(PlanDef plan);

    Optional<PlanDef> findPlan(String planCode);

    // ---- tenant_plan ----
    void saveTenantPlan(TenantPlan tenantPlan);

    Optional<TenantPlan> findTenantPlan(String tenantId);

    /** 全部租户套餐（cron 触发链遍历用） */
    List<TenantPlan> listTenantPlans();

    // ---- plan_capability_binding ----
    void saveBinding(PlanCapabilityBinding binding);

    Optional<PlanCapabilityBinding> findBinding(String planCode, String capabilityCode);

    // ---- policy_profile ----
    void savePolicy(PolicyProfile policy);

    Optional<PolicyProfile> findPolicy(String code, int version);

    // ---- quota_profile ----
    void saveQuota(QuotaProfile quota);

    Optional<QuotaProfile> findQuota(String code);

    // ---- capability_invoke_log ----
    void appendInvokeLog(CapabilityInvokeLog log);

    List<CapabilityInvokeLog> listInvokeLogs(String tenantId);

    // ---- budget authorization ----
    void setBudgetAuthorization(String tenantId, boolean valid);

    /** 预算授权是否有效（无记录默认有效） */
    boolean budgetAuthorizationValid(String tenantId);
}
