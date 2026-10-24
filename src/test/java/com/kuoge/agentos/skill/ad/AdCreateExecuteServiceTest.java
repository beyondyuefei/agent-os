package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.channel.AdCreateReceipt;
import com.kuoge.agentos.channel.CreationStatus;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemAction;
import com.kuoge.agentos.delivery.InMemoryDeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 广告创建执行测试（验收规则 5：幂等与未知处置）。
 */
class AdCreateExecuteServiceTest {

    private CapabilityStore store;
    private MockAdChannelAdapter channel;
    private InMemoryDeliveryItemStore repository;
    private DeliveryItemStateMachine sm;
    private AdCreateExecuteService service;
    private String tenant;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        tenant = CapabilityTestFixtures.TENANT_KA;   // KA 档 AUTO_LOW_RISK，风险动作放行
        channel = new MockAdChannelAdapter();
        repository = new InMemoryDeliveryItemStore();
        sm = new DeliveryItemStateMachine();
        CapabilityGateway gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> repository.findById(itemId)
                        .map(DeliveryItem::approvedAndLatest).orElse(false),
                CapabilityTestFixtures.noopNotifier());
        service = new AdCreateExecuteService(gateway, channel, repository, sm,
                new PreflightCheckService(repository, store));
    }

    /** 组装 → 送审 → 批准，得到已批准投放项 */
    private DeliveryItem approvedItem() {
        return approvedItem(10_000);
    }

    /** 组装 → 送审 → 批准，得到已批准投放项（可指定预算金额） */
    private DeliveryItem approvedItem(long budgetAmount) {
        com.kuoge.agentos.delivery.BudgetLedger ledger = new com.kuoge.agentos.delivery.InMemoryBudgetLedger();
        ledger.openAccount(tenant, 10_000_000);
        ItemAssembleService assemble = new ItemAssembleService(repository,
                ledger, collectionId -> true);
        DeliveryItem item = assemble.execute(SkillContext.of(tenant),
                new ItemAssembleService.Input("img_1", "p_1", "plan_1", "col_1", budgetAmount));
        sm.transit(item, DeliveryItemAction.SUBMIT, null);
        sm.transit(item, DeliveryItemAction.APPROVE, null);
        repository.save(item);
        return item;
    }

    @Test
    void createSuccess_fullFlow() {
        DeliveryItem item = approvedItem();
        AdCreateExecuteService.Output output = service.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        assertEquals(AdCreateReceipt.Status.SUCCESS, output.status());
        assertNotNull(output.platformAdId());
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
        assertEquals(1, channel.platformSubmissionCount());
    }

    /** 验收规则 5：同快照并发触发 10 次，系统仅提交 1 次创建请求 */
    @Test
    void rule5_concurrentTriggers_singlePlatformSubmission() throws Exception {
        DeliveryItem item = approvedItem();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AdCreateExecuteService.Output>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.execute(SkillContext.of(tenant), item.getDeliveryItemId());
            }));
        }
        start.countDown();
        for (Future<AdCreateExecuteService.Output> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(1, channel.platformSubmissionCount(),
                "并发触发 10 次，系统边界只应向平台提交 1 次创建请求");
    }

    /** 验收规则 5：重复触发命中已成功回执 → idempotent_replay，不重复提交 */
    @Test
    void rule5_repeatAfterSuccess_idempotentReplay() {
        DeliveryItem item = approvedItem();
        AdCreateExecuteService.Output first = service.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        AdCreateExecuteService.Output second = service.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        assertTrue(second.idempotentReplay());
        assertEquals(first.platformAdId(), second.platformAdId());
        assertEquals(1, channel.platformSubmissionCount());
    }

    /** 验收规则 5：未知结果不自动重复创建，进入待查；未确认前不得再次提交 */
    @Test
    void rule5_unknownResult_noAutoResubmit() {
        DeliveryItem item = approvedItem();
        channel.simulateNextCreateUnknown();
        AdCreateExecuteService.Output first = service.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        assertEquals(AdCreateReceipt.Status.UNKNOWN, first.status());
        assertEquals(DeliveryItemStatus.PENDING_CHECK, item.getStatus());
        // 未确认前再次触发 → 不提交平台
        AdCreateExecuteService.Output again = service.execute(SkillContext.of(tenant),
                item.getDeliveryItemId());
        assertEquals(AdCreateReceipt.Status.UNKNOWN, again.status());
        assertEquals(1, channel.platformSubmissionCount(), "未知结果不得自动重复创建");
        // 反查确认已创建 → 回到已创建
        assertEquals(CreationStatus.CREATED, channel.getCreationStatus(first.platformRequestId()));
        sm.transit(item, DeliveryItemAction.CONFIRM_CREATED, null);
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
    }

    /** 网关拒绝（套餐到期）→ 创建被阻断，不触达渠道 */
    @Test
    void gatewayRejected_createBlocked() {
        DeliveryItem item = approvedItem();
        store.saveTenantPlan(new TenantPlan(tenant, "PLAN_KA", 1,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), false));
        AdCreateExecuteService.GatewayRejectedException ex = assertThrows(
                AdCreateExecuteService.GatewayRejectedException.class,
                () -> service.execute(SkillContext.of(tenant), item.getDeliveryItemId()));
        assertTrue(ex.getMessage().contains("AUTH_002"));
        assertEquals(0, channel.platformSubmissionCount(), "网关拒绝后不得触达渠道");
    }

    /** 投前检查集 B 不过 → 阻断且不触达渠道（Mock 通道提交计数不变） */
    @Test
    void preflightBFailed_createBlocked_channelUntouched() {
        // 预算金额为 0 → A4_QUOTA_VALID 不过；网关（套餐/批准/预算授权）仍放行
        DeliveryItem item = approvedItem(0);
        PreflightBFailedException ex = assertThrows(PreflightBFailedException.class,
                () -> service.execute(SkillContext.of(tenant), item.getDeliveryItemId()));
        assertTrue(ex.getMessage().startsWith("PREFLIGHT_B_FAILED"));
        assertTrue(ex.failedCodes().contains("A4_QUOTA_VALID"));
        assertEquals(0, channel.platformSubmissionCount(), "preflight 不过不得触达渠道");
        assertEquals(DeliveryItemStatus.APPROVED, item.getStatus(), "preflight 不过状态不变");
    }
}
