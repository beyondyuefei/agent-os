package com.kuoge.agentos.capability;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 能力网关（方案重点一）：所有写路径的第一调用。
 * 判定矩阵（方案 2.2，研发的唯一事实来源）：
 * <pre>
 * 能力未开通            → REJECTED ENT_001
 * 套餐到期 / 已撤权      → 风险动作 REJECTED AUTH_002；停止类放行 + 通知负责人
 * 数据越权              → REJECTED DAT_006
 * 投放项未批准/版本失效  → REJECTED APR_005
 * 预算授权失效          → 风险动作 REJECTED BUD_004；停止类放行 + 通知
 * 策略要求自动、合同只到建议级 → DEGRADED（出建议卡片等人确认）
 * 全部通过              → ALLOW + 注入 PlanPolicy
 * </pre>
 * 域服务层独立生效：Agent 入口、任务重试、人工后台统一过网关。
 */
public class CapabilityGateway {

    private final CapabilityStore store;
    private final DeliveryItemApprovalProvider approvalProvider;
    private final RevocationNotifier notifier;

    public CapabilityGateway(CapabilityStore store,
                             DeliveryItemApprovalProvider approvalProvider,
                             RevocationNotifier notifier) {
        this.store = store;
        this.approvalProvider = approvalProvider;
        this.notifier = notifier;
    }

    /**
     * 网关判定。
     *
     * @param tenantId       租户
     * @param capabilityCode 目标能力
     * @param action         具体动作（风险等级由服务端注册表判定）
     * @param deliveryItemId 风险 / 停止动作必传
     * @param requestedData  本次动作涉及的数据集（数据范围轴检查）
     */
    public GatewayDecision check(String tenantId, CapabilityCode capabilityCode, String action,
                                 String deliveryItemId, List<String> requestedData) {
        LocalDateTime now = LocalDateTime.now();
        ActionType actionType = ActionType.of(action);

        // 1. 能力未开通 → ENT_001
        TenantPlan tenantPlan = store.findTenantPlan(tenantId).orElse(null);
        PlanCapabilityBinding binding = tenantPlan == null ? null
                : store.findBinding(tenantPlan.planCode(), capabilityCode.code()).orElse(null);
        if (tenantPlan == null || binding == null || !binding.enabled()
                || store.findCapability(capabilityCode.code()).map(c -> !c.active()).orElse(true)) {
            return log(tenantId, capabilityCode, action, GatewayDecision.rejected(RejectCode.ENT_001),
                    tenantPlan, null, null);
        }

        PolicyProfile policy = store
                .findPolicy(binding.policyProfileCode(), binding.policyProfileVersion())
                .orElse(null);

        // 2. 套餐到期 / 已撤权 → 风险动作拒 AUTH_002；停止类放行并通知
        if (!tenantPlan.effective(now)) {
            if (actionType == ActionType.RISK) {
                return log(tenantId, capabilityCode, action,
                        GatewayDecision.rejected(RejectCode.AUTH_002), tenantPlan, policy, binding);
            }
            notifier.notifyOwner(tenantId, "套餐到期或已撤权，停止类动作放行，新增类动作已阻断");
            return log(tenantId, capabilityCode, action,
                    GatewayDecision.allow(policy, binding.skillVersion()), tenantPlan, policy, binding);
        }

        // 3. 数据越权 → DAT_006
        List<String> requested = requestedData == null ? List.of() : requestedData;
        if (policy != null && !requested.isEmpty() && !policy.coversDataScope(requested)) {
            return log(tenantId, capabilityCode, action, GatewayDecision.rejected(RejectCode.DAT_006),
                    tenantPlan, policy, binding);
        }

        // 4. 投放项未批准 / 批准版本失效 → APR_005（仅风险动作需要）
        if (actionType == ActionType.RISK && deliveryItemId != null
                && approvalProvider != null
                && !approvalProvider.approvedAndLatest(tenantId, deliveryItemId)) {
            return log(tenantId, capabilityCode, action, GatewayDecision.rejected(RejectCode.APR_005),
                    tenantPlan, policy, binding);
        }

        // 5. 预算授权失效 → 风险动作拒 BUD_004；停止类放行并通知
        if (!store.budgetAuthorizationValid(tenantId)) {
            if (actionType == ActionType.RISK) {
                return log(tenantId, capabilityCode, action,
                        GatewayDecision.rejected(RejectCode.BUD_004), tenantPlan, policy, binding);
            }
            notifier.notifyOwner(tenantId, "预算授权失效，停止类动作放行，新增类动作已阻断");
            return log(tenantId, capabilityCode, action,
                    GatewayDecision.allow(policy, binding.skillVersion()), tenantPlan, policy, binding);
        }

        // 6. 策略要求自动执行、合同只到建议级 → DEGRADED
        if (actionType == ActionType.RISK && policy != null
                && policy.autonomyLevel() == AutonomyLevel.SUGGEST_ONLY) {
            return log(tenantId, capabilityCode, action,
                    GatewayDecision.degraded(policy, binding.skillVersion(),
                            "当前套餐为建议级（SUGGEST_ONLY），已降级为建议卡片，等人确认后执行"),
                    tenantPlan, policy, binding);
        }

        // 7. 全部通过 → ALLOW
        return log(tenantId, capabilityCode, action,
                GatewayDecision.allow(policy, binding.skillVersion()), tenantPlan, policy, binding);
    }

    /** 便捷重载：无数据范围 / 投放项上下文 */
    public GatewayDecision check(String tenantId, CapabilityCode capabilityCode, String action) {
        return check(tenantId, capabilityCode, action, null, List.of());
    }

    private GatewayDecision log(String tenantId, CapabilityCode capabilityCode, String action,
                                GatewayDecision decision, TenantPlan tenantPlan,
                                PolicyProfile policy, PlanCapabilityBinding binding) {
        store.appendInvokeLog(new CapabilityInvokeLog(
                tenantId,
                capabilityCode.code(),
                action,
                decision.decision(),
                decision.rejectCode() == null ? null : decision.rejectCode().name(),
                tenantPlan == null ? null : tenantPlan.planVersion(),
                policy == null ? null : policy.version(),
                binding == null ? null : binding.skillVersion(),
                decision.degradeNote(),
                LocalDateTime.now()));
        return decision;
    }
}
