package com.kuoge.agentos.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 能力网关判定矩阵测试（方案 2.2 / 验收规则 1、2、4）。
 */
class CapabilityGatewayTest {

    private CapabilityStore store;
    private CapabilityGateway gateway;
    private List<String> notifications;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        notifications = new ArrayList<>();
        gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> true,   // 默认已批准且最新
                (tenantId, reason) -> notifications.add(tenantId + ": " + reason));
    }

    /** 验收规则 1：能力未开通（PRO 租户触发素材生成）被 ENT_001 拒绝且留痕 */
    @Test
    void rule1_capabilityNotEntitled_rejectedWithEnt001() {
        GatewayDecision decision = gateway.check(CapabilityTestFixtures.TENANT_PRO,
                CapabilityCode.AD_CREATIVE_GENERATE, "CREATIVE_GENERATE");
        assertTrue(decision.rejected());
        assertEquals(RejectCode.ENT_001, decision.rejectCode());
        // 留痕
        List<CapabilityInvokeLog> logs = store.listInvokeLogs(CapabilityTestFixtures.TENANT_PRO);
        assertEquals(1, logs.size());
        assertEquals(Decision.REJECTED, logs.get(0).decision());
        assertEquals("ENT_001", logs.get(0).rejectCode());
    }

    /** 验收规则 2：KA 策略改 PRO 策略（SUGGEST_ONLY）→ 降级出建议卡片 */
    @Test
    void rule2_autoRequestOnSuggestOnlyPolicy_degraded() {
        GatewayDecision decision = gateway.check(CapabilityTestFixtures.TENANT_PRO,
                CapabilityCode.AD_EFFECT_EVOLVE, "EVOLVE_EXECUTE");
        assertEquals(Decision.DEGRADED, decision.decision());
        assertNotNull(decision.policy());
        assertEquals(AutonomyLevel.SUGGEST_ONLY, decision.policy().autonomyLevel());
        assertNotNull(decision.degradeNote());
    }

    /** KA 档（AUTO_LOW_RISK）风险动作直接放行并注入策略 */
    @Test
    void kaTenant_riskAction_allowedWithPolicy() {
        GatewayDecision decision = gateway.check(CapabilityTestFixtures.TENANT_KA,
                CapabilityCode.AD_EFFECT_EVOLVE, "EVOLVE_EXECUTE");
        assertTrue(decision.allowed());
        assertEquals(AutonomyLevel.AUTO_LOW_RISK, decision.policy().autonomyLevel());
        assertEquals("2.x", decision.skillVersion());
    }

    /** 验收规则 4：套餐到期后新建被拒（AUTH_002）、暂停仍放行、负责人收到通知 */
    @Test
    void rule4_planExpired_riskRejectedStopAllowedAndNotified() {
        String tenant = CapabilityTestFixtures.TENANT_PRO;
        // 构造到期租户
        store.saveTenantPlan(new TenantPlan(tenant, "PLAN_PRO", 1,
                LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(1), false));

        GatewayDecision create = gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "AD_CREATE");
        assertTrue(create.rejected());
        assertEquals(RejectCode.AUTH_002, create.rejectCode());

        GatewayDecision pause = gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "AD_PAUSE");
        assertTrue(pause.allowed());
        assertFalse(notifications.isEmpty(), "套餐到期应通知投流负责人");
    }

    /** 撤权租户：风险动作拒 AUTH_002，停止类放行 */
    @Test
    void revokedTenant_riskRejectedStopAllowed() {
        String tenant = CapabilityTestFixtures.TENANT_PRO;
        store.saveTenantPlan(new TenantPlan(tenant, "PLAN_PRO", 1,
                LocalDateTime.now().minusDays(30), LocalDateTime.now().plusDays(30), true));
        assertEquals(RejectCode.AUTH_002,
                gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "BUDGET_INCREASE").rejectCode());
        assertTrue(gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "AD_END").allowed());
    }

    /** 投放项未批准 / 版本失效 → APR_005 */
    @Test
    void itemNotApproved_rejectedApr005() {
        CapabilityGateway strictGateway = new CapabilityGateway(store,
                (tenantId, itemId) -> false, CapabilityTestFixtures.noopNotifier());
        GatewayDecision decision = strictGateway.check(CapabilityTestFixtures.TENANT_KA,
                CapabilityCode.AD_DELIVERY_OPS, "AD_CREATE", "di_1", List.of());
        assertTrue(decision.rejected());
        assertEquals(RejectCode.APR_005, decision.rejectCode());
    }

    /** 数据越权 → DAT_006 */
    @Test
    void dataScopeViolation_rejectedDat006() {
        // PRO 策略 dataScope 仅 ad_fact_platform / ad_fact_onsite，请求毛利数据越权
        GatewayDecision decision = gateway.check(CapabilityTestFixtures.TENANT_PRO,
                CapabilityCode.AD_EFFECT_EVOLVE, "EVOLVE_SUGGEST", null,
                List.of("ad_fact_platform", "product_margin"));
        assertTrue(decision.rejected());
        assertEquals(RejectCode.DAT_006, decision.rejectCode());
    }

    /** 预算授权失效：风险动作拒 BUD_004，停止类放行并通知 */
    @Test
    void budgetAuthorizationInvalid_riskRejectedStopAllowed() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        store.setBudgetAuthorization(tenant, false);
        GatewayDecision risk = gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "AD_CREATE");
        assertTrue(risk.rejected());
        assertEquals(RejectCode.BUD_004, risk.rejectCode());
        assertTrue(gateway.check(tenant, CapabilityCode.AD_DELIVERY_OPS, "AD_PAUSE").allowed());
        assertFalse(notifications.isEmpty());
    }

    /** 未知动作按风险动作处理（安全默认，风险等级调用方不得自报） */
    @Test
    void unknownAction_defaultsToRisk() {
        assertEquals(ActionType.RISK, ActionType.of("SOME_UNREGISTERED_ACTION"));
        assertEquals(ActionType.STOP, ActionType.of("AD_PAUSE"));
        assertEquals(ActionType.READ, ActionType.of("INSIGHT_QUERY"));
    }

    /** 只读动作不做授权检查但全量留痕 */
    @Test
    void readAction_logged() {
        gateway.check(CapabilityTestFixtures.TENANT_PRO, CapabilityCode.AD_DATA_OBSERVE, "INSIGHT_QUERY");
        assertEquals(1, store.listInvokeLogs(CapabilityTestFixtures.TENANT_PRO).size());
    }
}
