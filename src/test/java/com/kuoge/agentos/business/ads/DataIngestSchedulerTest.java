package com.kuoge.agentos.business.ads;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.InMemoryBudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.InMemoryDeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.ad.AdCreateExecuteService;
import com.kuoge.agentos.skill.ad.AdFactStore;
import com.kuoge.agentos.skill.ad.InMemoryAdFactStore;
import com.kuoge.agentos.skill.ad.AttributionMapService;
import com.kuoge.agentos.skill.ad.DataIngestService;
import com.kuoge.agentos.skill.ad.ExperienceDistillSkill;
import com.kuoge.agentos.skill.ad.ItemAssembleService;
import com.kuoge.agentos.skill.ad.OpsControlService;
import com.kuoge.agentos.skill.ad.PreflightCheckService;
import com.kuoge.agentos.skill.ad.ReviewDraftSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据回流 cron 触发链测试（方案「cron 读 policy」）：
 * 到期触发 ingest / 未到期不触发 / 单租户失败不影响其他租户。
 */
class DataIngestSchedulerTest {

    private CapabilityStore store;
    private InMemoryDeliveryItemStore repository;
    private MockAdChannelAdapter channel;
    private AdFactStore factStore;
    private DataIngestScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        repository = new InMemoryDeliveryItemStore();
        DeliveryItemStateMachine stateMachine = new DeliveryItemStateMachine();
        BudgetLedger budgetLedger = new InMemoryBudgetLedger();
        channel = new MockAdChannelAdapter();
        factStore = new InMemoryAdFactStore();
        InMemoryCollectionReadiness readiness = new InMemoryCollectionReadiness();
        CapabilityGateway gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> true, CapabilityTestFixtures.noopNotifier());
        PreflightCheckService preflight = new PreflightCheckService(repository, store);
        DeliveryPipelineService pipeline = new DeliveryPipelineService(repository, stateMachine,
                new ItemAssembleService(repository, budgetLedger, readiness), readiness, preflight,
                new AdCreateExecuteService(gateway, channel, repository, stateMachine, preflight),
                new OpsControlService(gateway, repository, stateMachine, channel, preflight),
                new DataIngestService(channel, factStore),
                new AttributionMapService(repository, factStore),
                new ReviewDraftSkill(null),
                new ExperienceDistillSkill(false, factStore),
                factStore, channel, gateway, store, null);
        scheduler = new DataIngestScheduler(store, pipeline);
    }

    /** 运行中且平台对象已创建的投放项 */
    private DeliveryItem runningItem(String tenant, String itemId) {
        DeliveryItem item = new DeliveryItem(itemId, tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.RUNNING);
        ApprovedItemSnapshot snapshot = new ApprovedItemSnapshot("snap_" + itemId, itemId, 1,
                1, 1, "1.x", "p", "img", "https://shop.example.com/c/col?featured=p",
                List.of("p"), 10_000, "RESERVED", List.of("image", "page", "quota"));
        item.setPlatformAdId(channel.createAd(snapshot).platformAdId());
        repository.save(item);
        return item;
    }

    /** cron 到期 → 触发该租户 ingest（每分钟粒度，跨过分钟边界即到期） */
    @Test
    void dueCron_triggersIngest() {
        CapabilityTestFixtures.bindDataObserve(store, "PLAN_KA", "* * * * *");
        CapabilityTestFixtures.bindDataObserve(store, "PLAN_PRO", "0 2 * * *");
        DeliveryItem kaItem = runningItem(CapabilityTestFixtures.TENANT_KA, "di_ka1");

        LocalDateTime t0 = LocalDateTime.of(2026, 10, 23, 10, 0, 30);
        scheduler.tick(t0);   // 首次 tick：登记基准，不补跑
        assertTrue(factStore.platformFacts(kaItem.getPlatformAdId()).isEmpty());

        scheduler.tick(t0.plusSeconds(61));   // 跨过 10:01:00 → KA 到期；PRO（每日 02:00）未到期
        assertFalse(factStore.platformFacts(kaItem.getPlatformAdId()).isEmpty(),
                "到期租户必须执行数据回流");
    }

    /** cron 未到期 → 不触发 */
    @Test
    void notDueCron_doesNotIngest() {
        CapabilityTestFixtures.bindDataObserve(store, "PLAN_PRO", "0 2 * * *");
        DeliveryItem proItem = runningItem(CapabilityTestFixtures.TENANT_PRO, "di_pro1");

        LocalDateTime t0 = LocalDateTime.of(2026, 10, 23, 10, 0, 30);
        scheduler.tick(t0);
        scheduler.tick(t0.plusHours(1));   // 每日 02:00 的 cron 在 11:00 仍不到期
        assertTrue(factStore.platformFacts(proItem.getPlatformAdId()).isEmpty(),
                "未到期不得触发数据回流");
    }

    /** 单租户回流异常（平台对象不存在）不影响其他租户，异常不向上传播 */
    @Test
    void singleTenantFailure_doesNotAffectOthers() {
        CapabilityTestFixtures.bindDataObserve(store, "PLAN_KA", "* * * * *");
        CapabilityTestFixtures.bindDataObserve(store, "PLAN_PRO", "* * * * *");
        DeliveryItem kaItem = runningItem(CapabilityTestFixtures.TENANT_KA, "di_ka2");
        // PRO 租户投放项的 platformAdId 未在平台创建 → fetchInsights 必失败
        DeliveryItem ghost = new DeliveryItem("di_ghost", CapabilityTestFixtures.TENANT_PRO,
                "img", "p", "plan", "col", DeliveryItemStatus.RUNNING);
        ghost.setPlatformAdId("ad_not_exist");
        repository.save(ghost);

        LocalDateTime t0 = LocalDateTime.of(2026, 10, 23, 10, 0, 30);
        scheduler.tick(t0);
        assertDoesNotThrow(() -> scheduler.tick(t0.plusSeconds(61)));
        assertFalse(factStore.platformFacts(kaItem.getPlatformAdId()).isEmpty(),
                "其他租户失败不得影响本租户回流");
    }

    /** 无 ad.data.observe 绑定 / cronExpr 为空 → 跳过不触发 */
    @Test
    void noBindingOrNoCron_skipped() {
        DeliveryItem kaItem = runningItem(CapabilityTestFixtures.TENANT_KA, "di_ka3");
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 23, 10, 0, 30);
        scheduler.tick(t0);
        scheduler.tick(t0.plusSeconds(61));
        assertTrue(factStore.platformFacts(kaItem.getPlatformAdId()).isEmpty(),
                "无绑定的租户不得触发数据回流");
    }
}
