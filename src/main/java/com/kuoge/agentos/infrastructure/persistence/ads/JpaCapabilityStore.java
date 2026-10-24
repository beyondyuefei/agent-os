package com.kuoge.agentos.infrastructure.persistence.ads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kuoge.agentos.capability.AutonomyLevel;
import com.kuoge.agentos.capability.CapabilityDef;
import com.kuoge.agentos.capability.CapabilityInvokeLog;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.Decision;
import com.kuoge.agentos.capability.PlanCapabilityBinding;
import com.kuoge.agentos.capability.PlanDef;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.capability.QuotaProfile;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.BudgetAuthorizationEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.CapabilityDefEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.CapabilityInvokeLogEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.PlanCapabilityBindingEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.PlanDefEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.PolicyProfileEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.QuotaProfileEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.TenantPlanEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAuthorizationRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityInvokeLogRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanCapabilityBindingRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PolicyProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.QuotaProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.TenantPlanRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 能力域 JPA 存储（方案 2.1 八表）：
 * capability_def / plan_def / tenant_plan / plan_capability_binding /
 * policy_profile / quota_profile / capability_invoke_log / budget_authorization。
 * policy 的 thresholds / modelRoute / dataScope 以 JSON 存 TEXT 列。
 */
public class JpaCapabilityStore implements CapabilityStore {

    private final CapabilityDefRepository capabilityRepository;
    private final PlanDefRepository planRepository;
    private final TenantPlanRepository tenantPlanRepository;
    private final PlanCapabilityBindingRepository bindingRepository;
    private final PolicyProfileRepository policyRepository;
    private final QuotaProfileRepository quotaRepository;
    private final CapabilityInvokeLogRepository invokeLogRepository;
    private final BudgetAuthorizationRepository budgetAuthorizationRepository;

    public JpaCapabilityStore(CapabilityDefRepository capabilityRepository,
                              PlanDefRepository planRepository,
                              TenantPlanRepository tenantPlanRepository,
                              PlanCapabilityBindingRepository bindingRepository,
                              PolicyProfileRepository policyRepository,
                              QuotaProfileRepository quotaRepository,
                              CapabilityInvokeLogRepository invokeLogRepository,
                              BudgetAuthorizationRepository budgetAuthorizationRepository) {
        this.capabilityRepository = capabilityRepository;
        this.planRepository = planRepository;
        this.tenantPlanRepository = tenantPlanRepository;
        this.bindingRepository = bindingRepository;
        this.policyRepository = policyRepository;
        this.quotaRepository = quotaRepository;
        this.invokeLogRepository = invokeLogRepository;
        this.budgetAuthorizationRepository = budgetAuthorizationRepository;
    }

    // ---- capability_def ----
    @Override
    public void saveCapability(CapabilityDef def) {
        CapabilityDefEntity entity = new CapabilityDefEntity();
        entity.setCode(def.code());
        entity.setName(def.name());
        entity.setBizLine(def.bizLine());
        entity.setStatus(def.status());
        capabilityRepository.save(entity);
    }

    @Override
    public Optional<CapabilityDef> findCapability(String code) {
        return capabilityRepository.findById(code)
                .map(e -> new CapabilityDef(e.getCode(), e.getName(), e.getBizLine(), e.getStatus()));
    }

    // ---- plan_def ----
    @Override
    public void savePlan(PlanDef plan) {
        PlanDefEntity entity = new PlanDefEntity();
        entity.setPlanCode(plan.planCode());
        entity.setName(plan.name());
        entity.setVersion(plan.version());
        entity.setStatus(plan.status());
        planRepository.save(entity);
    }

    @Override
    public Optional<PlanDef> findPlan(String planCode) {
        return planRepository.findById(planCode)
                .map(e -> new PlanDef(e.getPlanCode(), e.getName(), e.getVersion(), e.getStatus()));
    }

    // ---- tenant_plan ----
    @Override
    public void saveTenantPlan(TenantPlan tenantPlan) {
        TenantPlanEntity entity = new TenantPlanEntity();
        entity.setTenantId(tenantPlan.tenantId());
        entity.setPlanCode(tenantPlan.planCode());
        entity.setPlanVersion(tenantPlan.planVersion());
        entity.setEffectiveFrom(tenantPlan.effectiveFrom());
        entity.setEffectiveTo(tenantPlan.effectiveTo());
        entity.setRevoked(tenantPlan.revoked());
        tenantPlanRepository.save(entity);
    }

    @Override
    public Optional<TenantPlan> findTenantPlan(String tenantId) {
        return tenantPlanRepository.findById(tenantId).map(JpaCapabilityStore::toTenantPlan);
    }

    /** 全部租户套餐（cron 触发链遍历用） */
    @Override
    public List<TenantPlan> listTenantPlans() {
        return tenantPlanRepository.findAll().stream().map(JpaCapabilityStore::toTenantPlan).toList();
    }

    private static TenantPlan toTenantPlan(TenantPlanEntity e) {
        return new TenantPlan(e.getTenantId(), e.getPlanCode(), e.getPlanVersion(),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.isRevoked());
    }

    // ---- plan_capability_binding ----
    @Override
    public void saveBinding(PlanCapabilityBinding binding) {
        PlanCapabilityBindingEntity entity = new PlanCapabilityBindingEntity();
        entity.setId(PlanCapabilityBindingEntity.buildId(binding.planCode(), binding.capabilityCode()));
        entity.setPlanCode(binding.planCode());
        entity.setCapabilityCode(binding.capabilityCode());
        entity.setEnabled(binding.enabled());
        entity.setPolicyProfileCode(binding.policyProfileCode());
        entity.setPolicyProfileVersion(binding.policyProfileVersion());
        entity.setSkillPackageCode(binding.skillPackageCode());
        entity.setSkillVersion(binding.skillVersion());
        entity.setQuotaProfileCode(binding.quotaProfileCode());
        bindingRepository.save(entity);
    }

    @Override
    public Optional<PlanCapabilityBinding> findBinding(String planCode, String capabilityCode) {
        return bindingRepository.findById(PlanCapabilityBindingEntity.buildId(planCode, capabilityCode))
                .map(e -> new PlanCapabilityBinding(e.getPlanCode(), e.getCapabilityCode(),
                        e.isEnabled(), e.getPolicyProfileCode(), e.getPolicyProfileVersion(),
                        e.getSkillPackageCode(), e.getSkillVersion(), e.getQuotaProfileCode()));
    }

    // ---- policy_profile ----
    @Override
    public void savePolicy(PolicyProfile policy) {
        PolicyProfileEntity entity = new PolicyProfileEntity();
        entity.setId(PolicyProfileEntity.buildId(policy.code(), policy.version()));
        entity.setCode(policy.code());
        entity.setVersion(policy.version());
        entity.setAutonomyLevel(policy.autonomyLevel().name());
        entity.setCronExpr(policy.cronExpr());
        entity.setThresholdsJson(AdsJsonSupport.toJson(policy.thresholds()));
        entity.setModelRouteJson(AdsJsonSupport.toJson(policy.modelRoute()));
        entity.setDataScopeJson(AdsJsonSupport.toJson(policy.dataScope()));
        policyRepository.save(entity);
    }

    @Override
    public Optional<PolicyProfile> findPolicy(String code, int version) {
        return policyRepository.findById(PolicyProfileEntity.buildId(code, version))
                .map(e -> new PolicyProfile(e.getCode(), e.getVersion(),
                        AutonomyLevel.valueOf(e.getAutonomyLevel()), e.getCronExpr(),
                        e.getThresholdsJson() == null ? Map.of()
                                : AdsJsonSupport.fromJson(e.getThresholdsJson(),
                                        new TypeReference<Map<String, Object>>() {}),
                        e.getModelRouteJson() == null ? Map.of()
                                : AdsJsonSupport.fromJson(e.getModelRouteJson(),
                                        new TypeReference<Map<String, Object>>() {}),
                        e.getDataScopeJson() == null ? List.of()
                                : AdsJsonSupport.fromJson(e.getDataScopeJson(),
                                        new TypeReference<List<String>>() {})));
    }

    // ---- quota_profile ----
    @Override
    public void saveQuota(QuotaProfile quota) {
        QuotaProfileEntity entity = new QuotaProfileEntity();
        entity.setCode(quota.code());
        entity.setDailyLlmCalls(quota.dailyLlmCalls());
        entity.setDailyActions(quota.dailyActions());
        entity.setModelTier(quota.modelTier());
        quotaRepository.save(entity);
    }

    @Override
    public Optional<QuotaProfile> findQuota(String code) {
        return quotaRepository.findById(code)
                .map(e -> new QuotaProfile(e.getCode(), e.getDailyLlmCalls(),
                        e.getDailyActions(), e.getModelTier()));
    }

    // ---- capability_invoke_log ----
    @Override
    public void appendInvokeLog(CapabilityInvokeLog log) {
        CapabilityInvokeLogEntity entity = new CapabilityInvokeLogEntity();
        entity.setTenantId(log.tenantId());
        entity.setCapabilityCode(log.capabilityCode());
        entity.setAction(log.action());
        entity.setDecision(log.decision().name());
        entity.setRejectCode(log.rejectCode());
        entity.setPlanVersion(log.planVersion());
        entity.setPolicyVersion(log.policyVersion());
        entity.setSkillVersion(log.skillVersion());
        entity.setDegradeNote(log.degradeNote());
        entity.setCreatedAt(log.createdAt());
        invokeLogRepository.save(entity);
    }

    @Override
    public List<CapabilityInvokeLog> listInvokeLogs(String tenantId) {
        return invokeLogRepository.findByTenantIdOrderById(tenantId).stream()
                .map(e -> new CapabilityInvokeLog(e.getTenantId(), e.getCapabilityCode(),
                        e.getAction(), Decision.valueOf(e.getDecision()), e.getRejectCode(),
                        e.getPlanVersion(), e.getPolicyVersion(), e.getSkillVersion(),
                        e.getDegradeNote(), e.getCreatedAt()))
                .toList();
    }

    // ---- budget authorization ----
    @Override
    public void setBudgetAuthorization(String tenantId, boolean valid) {
        BudgetAuthorizationEntity entity = new BudgetAuthorizationEntity();
        entity.setTenantId(tenantId);
        entity.setValid(valid);
        budgetAuthorizationRepository.save(entity);
    }

    /** 无记录默认有效（对齐内存实现语义） */
    @Override
    public boolean budgetAuthorizationValid(String tenantId) {
        return budgetAuthorizationRepository.findById(tenantId)
                .map(BudgetAuthorizationEntity::isValid)
                .orElse(true);
    }
}
