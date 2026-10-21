package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.delivery.StateTransitionException;
import com.kuoge.agentos.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启停控制测试（方案 3.4 / 验收规则 4 的动作侧：撤权后 PAUSE 仍可执行且留痕）。
 */
class OpsControlServiceTest {

    private CapabilityStore store;
    private DeliveryItemRepository repository;
    private OpsControlService service;
    private String tenant;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        tenant = CapabilityTestFixtures.TENANT_KA;
        repository = new DeliveryItemRepository();
        CapabilityGateway gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> true, CapabilityTestFixtures.noopNotifier());
        service = new OpsControlService(gateway, repository, new DeliveryItemStateMachine());
    }

    private DeliveryItem runningItem() {
        DeliveryItem item = new DeliveryItem("di_1", tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.RUNNING);
        repository.save(item);
        return item;
    }

    @Test
    void pauseAndResume_withRecheck() {
        DeliveryItem item = runningItem();
        OpsControlService.Output paused = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.PAUSE));
        assertEquals("PAUSED", paused.newStatus());
        assertNotNull(paused.auditId());

        OpsControlService.Output resumed = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.RESUME));
        assertEquals("RUNNING", resumed.newStatus());
    }

    /** 撤权后：PAUSE 仍可执行且留痕（停止类不受套餐阻断），RESUME 被拒 */
    @Test
    void revokedTenant_pauseAllowedWithAudit_resumeRejected() {
        DeliveryItem item = runningItem();
        store.saveTenantPlan(new TenantPlan(tenant, "PLAN_KA", 1,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(10), true));

        OpsControlService.Output paused = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.PAUSE));
        assertEquals("PAUSED", paused.newStatus());
        assertTrue(repository.auditTrail().stream().anyMatch(e -> e.contains("PAUSE")),
                "停止类动作必须留痕");

        // RESUME 属风险动作：授权重检不过 → 拒绝，保持已暂停
        assertThrows(OpsControlService.AuthRecheckFailedException.class,
                () -> service.execute(SkillContext.of(tenant),
                        new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.RESUME)));
        assertEquals(DeliveryItemStatus.PAUSED, item.getStatus(), "重检不过保持已暂停并留痕");
    }

    /** 表外迁移：状态机兜底拒绝 */
    @Test
    void invalidTransition_rejectedByStateMachine() {
        DeliveryItem item = new DeliveryItem("di_2", tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.PENDING);
        repository.save(item);
        assertThrows(StateTransitionException.class,
                () -> service.execute(SkillContext.of(tenant),
                        new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.PAUSE)));
    }

    @Test
    void end_fromPaused_allowed() {
        DeliveryItem item = runningItem();
        service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.PAUSE));
        OpsControlService.Output ended = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.END));
        assertEquals("ENDED", ended.newStatus());
    }
}
