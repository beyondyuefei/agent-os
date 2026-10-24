package com.kuoge.agentos.business.ads;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.channel.AdCreateReceipt;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
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
import com.kuoge.agentos.skill.ad.EvolveSuggestSkill;
import com.kuoge.agentos.skill.ad.ExperienceDistillSkill;
import com.kuoge.agentos.skill.ad.ItemAssembleService;
import com.kuoge.agentos.skill.ad.OpsControlService;
import com.kuoge.agentos.skill.ad.PreflightCheckService;
import com.kuoge.agentos.skill.ad.ReviewDraftSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 投流编排层测试（方案 3.7 / SOP-A / SOP-B）：
 * SOP-A happy path 全链路状态序列与 Mock 通道计数、待补补齐、待查反查两路径、
 * 建议确认路由、版本线差异与经验沉淀审核。
 */
class DeliveryPipelineServiceTest {

    private CapabilityStore store;
    private InMemoryDeliveryItemStore repository;
    private BudgetLedger budgetLedger;
    private MockAdChannelAdapter channel;
    private AdFactStore factStore;
    private InMemoryCollectionReadiness readiness;
    private DeliveryPipelineService pipeline;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        repository = new InMemoryDeliveryItemStore();
        DeliveryItemStateMachine stateMachine = new DeliveryItemStateMachine();
        budgetLedger = new InMemoryBudgetLedger();
        budgetLedger.openAccount(CapabilityTestFixtures.TENANT_PRO, 1_000_000);
        budgetLedger.openAccount(CapabilityTestFixtures.TENANT_KA, 1_000_000);
        channel = new MockAdChannelAdapter();
        factStore = new InMemoryAdFactStore();
        readiness = new InMemoryCollectionReadiness();
        CapabilityGateway gateway = new CapabilityGateway(store,
                (tenantId, itemId) -> repository.findById(itemId)
                        .filter(item -> item.getTenantId().equals(tenantId))
                        .map(DeliveryItem::approvedAndLatest)
                        .orElse(false),
                CapabilityTestFixtures.noopNotifier());
        ItemAssembleService assembleService = new ItemAssembleService(repository, budgetLedger, readiness);
        PreflightCheckService preflight = new PreflightCheckService(repository, store);
        pipeline = new DeliveryPipelineService(repository, stateMachine, assembleService, readiness,
                preflight,
                new AdCreateExecuteService(gateway, channel, repository, stateMachine, preflight),
                new OpsControlService(gateway, repository, stateMachine, channel, preflight),
                new DataIngestService(channel, factStore),
                new AttributionMapService(repository, factStore),
                new ReviewDraftSkill(null),
                new ExperienceDistillSkill(false, factStore),
                factStore, channel, gateway, store, null);
    }

    private SkillContext ctx(String tenant) {
        return SkillContext.of(tenant);
    }

    /** SOP-A happy path：建项 → 送审 → 批准 → 创建 → 启用 → 暂停 → 恢复 → 结束 */
    @Test
    void sopA_happyPath_fullStatusSequenceAndChannelCounts() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        readiness.markReady("col1");
        DeliveryItem item = pipeline.assemble(ctx(tenant),
                new ItemAssembleService.Input("img1", "p1", "plan1", "col1", 10_000));
        String id = item.getDeliveryItemId();
        assertEquals(DeliveryItemStatus.PENDING, item.getStatus());
        assertNotNull(item.getApprovedSnapshot(), "母集合已就绪：组装即带批准快照");

        assertEquals(DeliveryItemStatus.READY_FOR_REVIEW, pipeline.submit(ctx(tenant), id).getStatus());
        assertEquals(DeliveryItemStatus.APPROVED, pipeline.approve(ctx(tenant), id, "boss").getStatus());

        AdCreateExecuteService.Output created = pipeline.create(ctx(tenant), id);
        assertEquals(AdCreateReceipt.Status.SUCCESS, created.status());
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
        assertNotNull(item.getPlatformAdId());
        assertEquals(1, channel.platformSubmissionCount(), "创建只提交平台一次");

        assertEquals("RUNNING", pipeline.ops(ctx(tenant), id, OpsControlService.Action.ENABLE).newStatus());
        assertEquals(1, channel.enableCallCount());
        assertEquals("PAUSED", pipeline.ops(ctx(tenant), id, OpsControlService.Action.PAUSE).newStatus());
        assertEquals(1, channel.pauseCallCount());
        assertEquals("RUNNING", pipeline.ops(ctx(tenant), id, OpsControlService.Action.RESUME).newStatus());
        assertEquals(2, channel.enableCallCount());
        assertEquals("ENDED", pipeline.ops(ctx(tenant), id, OpsControlService.Action.END).newStatus());
        assertEquals(1, channel.endCallCount());
    }

    /** 待补路径：母集合未就绪 → PENDING 无快照；送审被 preflight A 阻断；就绪后补齐再送审 */
    @Test
    void pendingItem_blockedThenCompletedAfterCollectionReady() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        DeliveryItem item = pipeline.assemble(ctx(tenant),
                new ItemAssembleService.Input("img1", "p1", "plan1", "col1", 10_000));
        String id = item.getDeliveryItemId();
        assertEquals(DeliveryItemStatus.PENDING, item.getStatus());
        assertNull(item.getApprovedSnapshot(), "母集合未就绪：待补不组装快照");

        DeliveryItem blocked = pipeline.submit(ctx(tenant), id);
        assertEquals(DeliveryItemStatus.PENDING, blocked.getStatus(), "preflight A 不过保持待补");
        assertTrue(blocked.getRejectReason().contains("PREFLIGHT_A_FAILED"));

        var completed = pipeline.collectionReady(ctx(tenant), "col1");
        assertEquals(1, completed.size(), "就绪后该集合下待补项应被补齐");
        assertNotNull(item.getApprovedSnapshot());
        assertEquals(DeliveryItemStatus.PENDING, item.getStatus(), "补齐后仍待补，等送审");

        assertEquals(DeliveryItemStatus.READY_FOR_REVIEW, pipeline.submit(ctx(tenant), id).getStatus());
    }

    /** 额度不足：预留与未分配余额都不够 → 抛额度不足（occupyUnallocated 返回值检查修复） */
    @Test
    void assemble_quotaInsufficient_throws() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        readiness.markReady("col1");
        assertThrows(ItemAssembleService.QuotaInsufficientException.class,
                () -> pipeline.assemble(ctx(tenant),
                        new ItemAssembleService.Input("img1", "p1", "plan1", "col1", 2_000_000)));
    }

    /** 待查反查（SOP-A Step 8）：平台实际已创建 → CONFIRM_CREATED 并补回 platformAdId */
    @Test
    void resolvePendingCheck_created_path() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        readiness.markReady("col1");
        DeliveryItem item = pipeline.assemble(ctx(tenant),
                new ItemAssembleService.Input("img1", "p1", "plan1", "col1", 10_000));
        String id = item.getDeliveryItemId();
        pipeline.submit(ctx(tenant), id);
        pipeline.approve(ctx(tenant), id, "boss");

        channel.simulateNextCreateUnknown();
        AdCreateExecuteService.Output output = pipeline.create(ctx(tenant), id);
        assertEquals(AdCreateReceipt.Status.UNKNOWN, output.status());
        assertEquals(DeliveryItemStatus.PENDING_CHECK, item.getStatus());
        assertNull(item.getPlatformAdId());

        pipeline.resolvePendingCheck(ctx(tenant), id);
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
        assertNotNull(item.getPlatformAdId(), "反查确认已创建应补回 platformAdId");
        assertTrue(channel.adExists(item.getPlatformAdId()));
    }

    /** 待查反查：仍未知 → 保持 PENDING_CHECK，不迁移 */
    @Test
    void resolvePendingCheck_stillUnknown_keepsPendingCheck() {
        String tenant = CapabilityTestFixtures.TENANT_KA;
        DeliveryItem item = new DeliveryItem("di_lost", tenant, "img", "p", "plan", "col",
                DeliveryItemStatus.PENDING_CHECK);
        item.setPlatformRequestId("req_never_seen");
        repository.save(item);

        pipeline.resolvePendingCheck(ctx(tenant), "di_lost");
        assertEquals(DeliveryItemStatus.PENDING_CHECK, item.getStatus(), "仍未知保持待查");
    }

    /** 进化建议版本线路由：KA（2.x）出实验设计；PRO（1.x）不出（方案硬要求：由绑定 skill_version 决定） */
    @Test
    void evolveSuggest_routesByBindingSkillVersion() {
        readiness.markReady("col1");
        DeliveryItem kaItem = pipeline.assemble(ctx(CapabilityTestFixtures.TENANT_KA),
                new ItemAssembleService.Input("img1", "p1", "plan1", "col1", 10_000));
        DeliveryItem proItem = pipeline.assemble(ctx(CapabilityTestFixtures.TENANT_PRO),
                new ItemAssembleService.Input("img2", "p2", "plan1", "col1", 10_000));

        EvolveSuggestSkill.Suggestion ka = pipeline.evolveSuggest(
                ctx(CapabilityTestFixtures.TENANT_KA), kaItem.getDeliveryItemId());
        assertEquals("OPS_CONTROL", ka.actionRoute(), "KA 策略 AUTO_LOW_RISK → 启停路由");
        assertNotNull(ka.experimentDesign(), "KA 版本线应输出实验设计");

        EvolveSuggestSkill.Suggestion pro = pipeline.evolveSuggest(
                ctx(CapabilityTestFixtures.TENANT_PRO), proItem.getDeliveryItemId());
        assertEquals("NONE", pro.actionRoute(), "PRO 策略 SUGGEST_ONLY → 仅建议卡片");
        assertNull(pro.experimentDesign(), "PRO 版本线不出实验设计");
    }

    /** 建议确认路由：OPS_CONTROL → 真实执行启停；NONE → 只留痕不执行（铁律：未确认不执行） */
    @Test
    void confirmSuggestion_routesByActionRoute() {
        readiness.markReady("col1");
        // KA：OPS_CONTROL 路由 → 执行 PAUSE
        DeliveryItem kaItem = assembleRunning(CapabilityTestFixtures.TENANT_KA);
        DeliveryPipelineService.SuggestionConfirmResult kaResult = pipeline.confirmSuggestion(
                ctx(CapabilityTestFixtures.TENANT_KA), kaItem.getDeliveryItemId(),
                OpsControlService.Action.PAUSE);
        assertEquals("OPS_CONTROL", kaResult.actionRoute());
        assertEquals(DeliveryItemStatus.PAUSED, kaItem.getStatus());
        assertEquals(1, channel.pauseCallCount());

        // PRO：NONE 路由 → 不执行，仅审计留痕
        DeliveryItem proItem = assembleRunning(CapabilityTestFixtures.TENANT_PRO);
        DeliveryPipelineService.SuggestionConfirmResult proResult = pipeline.confirmSuggestion(
                ctx(CapabilityTestFixtures.TENANT_PRO), proItem.getDeliveryItemId(),
                OpsControlService.Action.PAUSE);
        assertEquals("NONE", proResult.actionRoute());
        assertEquals(DeliveryItemStatus.RUNNING, proItem.getStatus(), "非 OPS_CONTROL 路由不得执行启停");
        assertEquals(1, channel.pauseCallCount(), "PRO 确认不得触达平台");
        assertTrue(repository.auditTrail().stream()
                        .anyMatch(e -> e.contains("SUGGESTION_CONFIRMED") && e.contains(proItem.getDeliveryItemId())),
                "未执行的建议确认必须留痕");
    }

    /** 复盘确认 → 经验沉淀：KA 自动入库（候选），审核后生效（SOP-B Step 7） */
    @Test
    void confirmReviewAndDistill_kaLineThenApprove() {
        readiness.markReady("col1");
        DeliveryItem kaItem = assembleRunning(CapabilityTestFixtures.TENANT_KA);
        AdFactStore.Experience candidate = pipeline.confirmReviewAndDistill(
                ctx(CapabilityTestFixtures.TENANT_KA), kaItem.getDeliveryItemId(), "CTR 低时优先换素材");
        assertFalse(candidate.approved(), "候选未审核不生效");
        assertTrue(factStore.allExperiences().stream().anyMatch(e -> e.id().equals(candidate.id())),
                "KA 版本线候选自动入库");
        assertTrue(factStore.effectiveExperiences().isEmpty(), "未审核规则不可被读为生效配置");

        AdFactStore.Experience approved = pipeline.approveExperience(
                ctx(CapabilityTestFixtures.TENANT_KA), candidate.id());
        assertTrue(approved.approved());
        assertTrue(factStore.effectiveExperiences().stream().anyMatch(e -> e.id().equals(candidate.id())));
    }

    /** 走完整 SOP-A 到 RUNNING 的投放项 */
    private DeliveryItem assembleRunning(String tenant) {
        DeliveryItem item = pipeline.assemble(ctx(tenant),
                new ItemAssembleService.Input("img_" + tenant, "p_" + tenant, "plan1", "col1", 10_000));
        String id = item.getDeliveryItemId();
        pipeline.submit(ctx(tenant), id);
        pipeline.approve(ctx(tenant), id, "boss");
        pipeline.create(ctx(tenant), id);
        pipeline.ops(ctx(tenant), id, OpsControlService.Action.ENABLE);
        return item;
    }
}
