package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.InMemoryDeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.delivery.StateTransitionException;
import com.kuoge.agentos.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启停控制测试（方案 3.4 / 验收规则 4 的动作侧：撤权后 PAUSE 仍可执行且留痕）。
 * 启停动作回执驱动：先调通道、成功才迁移状态；通道失败状态不变且抛 CHANNEL_UNAVAILABLE。
 */
class OpsControlServiceTest {

    private CapabilityStore store;
    private InMemoryDeliveryItemStore repository;
    private MockAdChannelAdapter channel;
    private OpsControlService service;
    private String tenant;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        tenant = CapabilityTestFixtures.TENANT_KA;
        repository = new InMemoryDeliveryItemStore();
        channel = new MockAdChannelAdapter();
        CapabilityGateway gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> true, CapabilityTestFixtures.noopNotifier());
        service = new OpsControlService(gateway, repository, new DeliveryItemStateMachine(),
                channel, new PreflightCheckService(repository, store));
    }

    /** 已在平台创建成功的投放项：批准快照 + platformAdId 齐备（preflight B 与通道调用前置） */
    private DeliveryItem createdItem(String itemId, DeliveryItemStatus status) {
        DeliveryItem item = new DeliveryItem(itemId, tenant, "img", "p", "plan", "col", status);
        ApprovedItemSnapshot snapshot = new ApprovedItemSnapshot("snap_" + itemId, itemId, 1, 1, 1, "1.x",
                "p", "img", "https://shop.example.com/c/col?featured=p",
                List.of("p"), 10_000, "RESERVED", List.of("image", "page", "quota"));
        item.setApprovedSnapshot(snapshot);
        item.setPlatformAdId(channel.createAd(snapshot).platformAdId());
        repository.save(item);
        return item;
    }

    private DeliveryItem runningItem() {
        return createdItem("di_1", DeliveryItemStatus.RUNNING);
    }

    @Test
    void pauseAndResume_withRecheck() {
        DeliveryItem item = runningItem();
        OpsControlService.Output paused = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.PAUSE));
        assertEquals("PAUSED", paused.newStatus());
        assertNotNull(paused.auditId());
        assertEquals(1, channel.pauseCallCount(), "PAUSE 必须真实调用平台通道");

        OpsControlService.Output resumed = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.RESUME));
        assertEquals("RUNNING", resumed.newStatus());
        assertEquals(1, channel.enableCallCount(), "RESUME 必须真实调用平台通道");
    }

    /** ENABLE：已创建项启用后平台通道 enable 被调用，状态迁移为 RUNNING */
    @Test
    void enable_callsChannelThenTransits() {
        DeliveryItem item = createdItem("di_enable", DeliveryItemStatus.CREATED);
        OpsControlService.Output output = service.execute(SkillContext.of(tenant),
                new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.ENABLE));
        assertEquals("RUNNING", output.newStatus());
        assertEquals(DeliveryItemStatus.RUNNING, item.getStatus());
        assertEquals(1, channel.enableCallCount(), "ENABLE 必须真实调用平台通道");
    }

    /** 通道调用失败：状态不迁移、不落库，留痕后抛 CHANNEL_UNAVAILABLE */
    @Test
    void channelFailure_statusUnchanged_throwsChannelUnavailable() {
        // platformAdId 未在平台创建（Mock 中不存在）→ 通道调用必失败
        DeliveryItem item = new DeliveryItem("di_ghost", tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.CREATED);
        item.setApprovedSnapshot(new ApprovedItemSnapshot("snap_ghost", "di_ghost", 1, 1, 1, "1.x",
                "p", "img", "https://shop.example.com/c/col?featured=p",
                List.of("p"), 10_000, "RESERVED", List.of("image", "page", "quota")));
        item.setPlatformAdId("ad_not_exist");
        repository.save(item);

        OpsControlService.ChannelUnavailableException ex = assertThrows(
                OpsControlService.ChannelUnavailableException.class,
                () -> service.execute(SkillContext.of(tenant),
                        new OpsControlService.Input(item.getDeliveryItemId(), OpsControlService.Action.ENABLE)));
        assertTrue(ex.getMessage().startsWith("CHANNEL_UNAVAILABLE"));
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus(), "通道失败不得迁移状态");
        assertEquals(0, channel.enableCallCount(), "通道未成功调用不计数");
        assertTrue(repository.auditTrail().stream().anyMatch(e -> e.contains("channel call failed")),
                "通道失败必须留痕");
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
        assertEquals(1, channel.endCallCount(), "END 必须真实调用平台通道");
    }
}
