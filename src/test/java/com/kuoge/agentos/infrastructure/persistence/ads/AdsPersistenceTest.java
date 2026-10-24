package com.kuoge.agentos.infrastructure.persistence.ads;

import com.kuoge.agentos.capability.CapabilityInvokeLog;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.Decision;
import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AdFactPlatformRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AttributionRecordRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AuditEntryRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAccountRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAuthorizationRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityInvokeLogRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.DeliveryItemJpaRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.ExperienceRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.GapTagRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanCapabilityBindingRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PolicyProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.QuotaProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.TenantPlanRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.UnknownOrderRepository;
import com.kuoge.agentos.skill.ad.AdFactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ads 持久层语义测试：四个 Jpa 实现与 InMemory 实现语义一致。
 * 覆盖：DeliveryItem 存取往返（含 snapshot JSON）、CapabilityStore 八表 CRUD 与
 * invoke log 追加、BudgetLedger 恒等式、AdFactStore 生效经验门禁（验收规则 9）。
 */
@DataJpaTest
class AdsPersistenceTest {

    @Autowired private DeliveryItemJpaRepository itemRepository;
    @Autowired private AuditEntryRepository auditRepository;
    @Autowired private CapabilityDefRepository capabilityRepository;
    @Autowired private PlanDefRepository planRepository;
    @Autowired private TenantPlanRepository tenantPlanRepository;
    @Autowired private PlanCapabilityBindingRepository bindingRepository;
    @Autowired private PolicyProfileRepository policyRepository;
    @Autowired private QuotaProfileRepository quotaRepository;
    @Autowired private CapabilityInvokeLogRepository invokeLogRepository;
    @Autowired private BudgetAuthorizationRepository budgetAuthorizationRepository;
    @Autowired private BudgetAccountRepository budgetAccountRepository;
    @Autowired private AdFactPlatformRepository platformRepository;
    @Autowired private AttributionRecordRepository attributionRepository;
    @Autowired private UnknownOrderRepository unknownOrderRepository;
    @Autowired private ExperienceRepository experienceRepository;
    @Autowired private GapTagRepository gapTagRepository;

    private JpaDeliveryItemStore itemStore;
    private JpaCapabilityStore capabilityStore;
    private JpaBudgetLedger budgetLedger;
    private JpaAdFactStore factStore;

    @BeforeEach
    void setUp() {
        itemStore = new JpaDeliveryItemStore(itemRepository, auditRepository);
        capabilityStore = new JpaCapabilityStore(capabilityRepository, planRepository,
                tenantPlanRepository, bindingRepository, policyRepository, quotaRepository,
                invokeLogRepository, budgetAuthorizationRepository);
        budgetLedger = new JpaBudgetLedger(budgetAccountRepository);
        factStore = new JpaAdFactStore(platformRepository, attributionRepository,
                unknownOrderRepository, experienceRepository, gapTagRepository);
    }

    // ---- DeliveryItemStore ----

    @Test
    void deliveryItem_roundTrip_withSnapshotJson() {
        DeliveryItem item = new DeliveryItem("di_1", "t1", "img1", "p1", "plan1", "col1",
                DeliveryItemStatus.APPROVED);
        item.setBudgetAmount(10_000);
        item.setPlatformRequestId("req_1");
        item.setPlatformAdId("ad_1");
        ApprovedItemSnapshot snapshot = new ApprovedItemSnapshot(
                "snap_1", "di_1", 1, 1, 3, "1.x", "p1", "img1",
                "https://sub/link", List.of("p1", "p2"), 10_000, "RESERVED", List.of("c1"));
        item.setApprovedSnapshot(snapshot);
        itemStore.save(item);

        DeliveryItem loaded = itemStore.findById("di_1").orElseThrow();
        assertEquals("t1", loaded.getTenantId());
        assertEquals(DeliveryItemStatus.APPROVED, loaded.getStatus());
        assertEquals(1, loaded.getVersion());
        assertEquals(10_000, loaded.getBudgetAmount());
        assertEquals("req_1", loaded.getPlatformRequestId());
        assertEquals("ad_1", loaded.getPlatformAdId());
        // snapshot JSON 往返：record 全字段相等
        assertEquals(snapshot, loaded.getApprovedSnapshot());
        // approvedAndLatest 语义：快照版本 == 当前版本
        assertTrue(loaded.approvedAndLatest());
        assertEquals(List.of("p1", "p2"), loaded.getApprovedSnapshot().ordering());

        // 快照版本落后于当前版本 → 批准失效（方案 2.2 APR_005）
        DeliveryItem stale = new DeliveryItem("di_2", "t1", "img2", "p2", "plan1", "col1",
                DeliveryItemStatus.APPROVED);
        stale.setApprovedSnapshot(new ApprovedItemSnapshot(
                "snap_2", "di_2", 99, 1, 3, "1.x", "p2", "img2",
                "https://sub/link2", List.of(), 1_000, "UNALLOCATED", List.of()));
        itemStore.save(stale);
        assertFalse(itemStore.findById("di_2").orElseThrow().approvedAndLatest());

        // findByTenant + 覆盖保存
        item.setRejectReason("被退回");
        itemStore.save(item);
        assertEquals(2, itemStore.findByTenant("t1").size());
        assertEquals("被退回", itemStore.findById("di_1").orElseThrow().getRejectReason());
        assertTrue(itemStore.findByTenant("t_other").isEmpty());
    }

    @Test
    void deliveryItem_auditTrail_persistedInOrder() {
        itemStore.appendAudit("PAUSE di_1 by op1");
        itemStore.appendAudit("END di_1 by op1");
        assertEquals(List.of("PAUSE di_1 by op1", "END di_1 by op1"), itemStore.auditTrail());
    }

    // ---- CapabilityStore ----

    @Test
    void capabilityStore_fixturesRoundTrip() {
        CapabilityTestFixtures.populate(capabilityStore);

        assertTrue(capabilityStore.findCapability("ad.delivery.ops").isPresent());
        assertEquals("进阶版", capabilityStore.findPlan("PLAN_PRO").orElseThrow().name());
        assertEquals("PLAN_KA", capabilityStore.findTenantPlan(CapabilityTestFixtures.TENANT_KA)
                .orElseThrow().planCode());
        assertEquals(2, capabilityStore.listTenantPlans().size());

        var binding = capabilityStore.findBinding("PLAN_KA", "ad.effect.evolve").orElseThrow();
        assertEquals("EVOLVE_KA", binding.policyProfileCode());
        assertEquals(5, binding.policyProfileVersion());
        assertEquals("2.x", binding.skillVersion());

        // policy JSON 列往返：六轴字段完整
        var policy = capabilityStore.findPolicy("EVOLVE_KA", 5).orElseThrow();
        assertEquals("0 * * * *", policy.cronExpr());
        assertEquals(1.5, ((Number) policy.thresholds().get("spend_no_order_ratio")).doubleValue());
        assertEquals(800, ((Number) policy.thresholds().get("min_impressions")).intValue());
        assertEquals("qwen-max", policy.modelRoute().get("primary"));
        assertTrue(policy.coversDataScope(List.of("ad_fact_platform", "refund")));

        var quota = capabilityStore.findQuota("QUOTA_KA").orElseThrow();
        assertEquals(1000, quota.dailyLlmCalls());
    }

    @Test
    void capabilityStore_invokeLogAppendAndBudgetAuthorization() {
        capabilityStore.appendInvokeLog(new CapabilityInvokeLog("t1", "ad.delivery.ops", "PAUSE",
                Decision.ALLOW, null, 1, 1, "1.x", null, LocalDateTime.now()));
        capabilityStore.appendInvokeLog(new CapabilityInvokeLog("t1", "ad.creative.generate", "RUN",
                Decision.REJECTED, "ENT_001", 1, 3, "1.x", null, LocalDateTime.now()));
        capabilityStore.appendInvokeLog(new CapabilityInvokeLog("t2", "ad.delivery.ops", "PAUSE",
                Decision.DEGRADED, null, 1, 1, "1.x", "预算授权失效降级", LocalDateTime.now()));

        var t1Logs = capabilityStore.listInvokeLogs("t1");
        assertEquals(2, t1Logs.size());
        assertEquals(Decision.REJECTED, t1Logs.get(1).decision());
        assertEquals("ENT_001", t1Logs.get(1).rejectCode());
        assertEquals(1, capabilityStore.listInvokeLogs("t2").size());
        assertEquals("预算授权失效降级", capabilityStore.listInvokeLogs("t2").get(0).degradeNote());

        // 预算授权：无记录默认有效；显式置否后失效
        assertTrue(capabilityStore.budgetAuthorizationValid("t1"));
        capabilityStore.setBudgetAuthorization("t1", false);
        assertFalse(capabilityStore.budgetAuthorizationValid("t1"));
        capabilityStore.setBudgetAuthorization("t1", true);
        assertTrue(capabilityStore.budgetAuthorizationValid("t1"));
    }

    // ---- BudgetLedger ----

    @Test
    void budgetLedger_invariantHoldsAcrossAllOps() {
        budgetLedger.openAccount("t1", 100_000);
        assertTrue(budgetLedger.reserve("t1", 30_000));
        assertTrue(budgetLedger.assertInvariant("t1"));

        budgetLedger.occupyFromReserved("t1", 20_000);
        assertTrue(budgetLedger.assertInvariant("t1"));
        assertEquals(10_000, budgetLedger.account("t1").reserved());
        assertEquals(20_000, budgetLedger.account("t1").occupied());

        assertTrue(budgetLedger.occupyUnallocated("t1", 5_000));
        assertFalse(budgetLedger.occupyUnallocated("t1", 200_000));   // 余额不足拒绝
        assertTrue(budgetLedger.assertInvariant("t1"));

        budgetLedger.settle("t1", 12_000);   // 占用 25_000 → 转已花
        assertTrue(budgetLedger.assertInvariant("t1"));
        assertEquals(13_000, budgetLedger.account("t1").occupied());
        assertEquals(12_000, budgetLedger.account("t1").spent());

        // 对账差异挂账不抹平
        budgetLedger.reconcile("t1", 12_500);
        assertEquals(500, budgetLedger.account("t1").discrepancy());
        assertTrue(budgetLedger.assertInvariant("t1"));

        // 预留不足抛异常（对齐内存实现）
        assertThrows(IllegalStateException.class,
                () -> budgetLedger.occupyFromReserved("t1", 999_999));
        assertThrows(IllegalStateException.class, () -> budgetLedger.account("t_none"));
    }

    // ---- AdFactStore ----

    @Test
    void adFactStore_platformFactAndAttributionRoundTrip() {
        InsightReport report = new InsightReport("ad_1",
                new Window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)),
                50_000, 10_000, 800,
                List.of(new InsightReport.AttributedOrder("o1", "pA", 3_000)));
        factStore.savePlatformFact(report);

        List<InsightReport> facts = factStore.platformFacts("ad_1");
        assertEquals(1, facts.size());
        assertEquals(report, facts.get(0));   // JSON 往返 record 全字段相等（含窗口与订单）
        assertTrue(factStore.platformFacts("ad_none").isEmpty());

        factStore.saveAttribution(new AdFactStore.AttributionRecord("o1", "pA", "pB", 3_000));
        assertEquals(1, factStore.attributionRecords().size());
        assertEquals("pB", factStore.attributionRecords().get(0).purchasedProductId());

        // 关联不明保持 unknown
        factStore.saveUnknownOrder(new InsightReport.AttributedOrder("o2", "pC", 1_000));
        assertEquals(1, factStore.unknownOrders().size());
        assertEquals("o2", factStore.unknownOrders().get(0).orderId());

        factStore.markGap("pX 未回传");
        assertEquals(List.of("pX 未回传"), factStore.gaps());
    }

    @Test
    void adFactStore_effectiveExperiences_onlyApproved() {
        factStore.saveExperience(new AdFactStore.Experience(
                "exp_1", "高毛利品前置", "review:t1", "CTR>2%", false));
        // 未审核规则不可被读为生效配置（验收规则 9）
        assertTrue(factStore.effectiveExperiences().isEmpty());
        assertEquals(1, factStore.allExperiences().size());

        factStore.saveExperience(new AdFactStore.Experience(
                "exp_1", "高毛利品前置", "review:t1", "CTR>2%", true));
        List<AdFactStore.Experience> effective = factStore.effectiveExperiences();
        assertEquals(1, effective.size());
        assertEquals("exp_1", effective.get(0).id());
        assertTrue(effective.get(0).approved());
    }
}
