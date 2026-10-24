package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.InMemoryDeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 投前校验测试（方案 3.6 ② 验收口径：A 集 6 项 + B 集 3 项，每项检查独立单测）。
 */
class PreflightCheckServiceTest {

    private CapabilityStore store;
    private InMemoryDeliveryItemStore repository;
    private PreflightCheckService service;
    private String tenant;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        tenant = CapabilityTestFixtures.TENANT_KA;
        repository = new InMemoryDeliveryItemStore();
        service = new PreflightCheckService(repository, store);
    }

    private ApprovedItemSnapshot snapshot(String itemId, int itemVersion,
                                          long budgetAmount, List<String> conditions) {
        return new ApprovedItemSnapshot("snap_" + itemId, itemId, itemVersion, 1, 1, "1.x",
                "p_1", "img_1", "https://shop.example.com/c/col_1?featured=p_1",
                List.of("p_1"), budgetAmount, "RESERVED", conditions);
    }

    /** 全要素齐备的已批准投放项（A 集 / B 集全过的基准形态） */
    private DeliveryItem validItem() {
        DeliveryItem item = new DeliveryItem("di_1", tenant, "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(snapshot("di_1", 1, 10_000, List.of("image", "page", "quota")));
        repository.save(item);
        return item;
    }

    private PreflightCheckService.Report run(DeliveryItem item, PreflightCheckService.CheckSet set) {
        return service.execute(SkillContext.of(tenant),
                new PreflightCheckService.Input(item.getDeliveryItemId(), set));
    }

    private PreflightCheckService.Check check(PreflightCheckService.Report report, String code) {
        return report.checks().stream().filter(c -> c.code().equals(code))
                .findFirst().orElseThrow(() -> new AssertionError("缺少检查项 " + code));
    }

    // ---- 基准：齐备形态全过 ----

    @Test
    void checkSetA_allPass_whenAssembled() {
        PreflightCheckService.Report report = run(validItem(), PreflightCheckService.CheckSet.A);
        assertTrue(report.pass());
        assertEquals(6, report.checks().size(), "A 集固定 6 项");
    }

    @Test
    void checkSetB_allPass_whenApprovedAndAuthorized() {
        PreflightCheckService.Report report = run(validItem(), PreflightCheckService.CheckSet.B);
        assertTrue(report.pass());
        assertEquals(9, report.checks().size(), "B 集 = A 6 项 + 授权 3 项");
    }

    // ---- A 集（送审前，装配齐备缺一不可）----

    /** A1：商品在售且信息未变更 */
    @Test
    void a1_fails_whenProductMissing() {
        DeliveryItem item = validItem();
        DeliveryItem noProduct = new DeliveryItem("di_a1", tenant, "img_1", null, "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        noProduct.setApprovedSnapshot(snapshot("di_a1", 1, 10_000, List.of("image")));
        repository.save(noProduct);
        PreflightCheckService.Report report = run(noProduct, PreflightCheckService.CheckSet.A);
        assertFalse(report.pass());
        assertFalse(check(report, "A1_PRODUCT_ON_SALE").pass());
        assertTrue(check(report, "A3_IMAGE_CONSISTENT").pass(), "其余项不受影响");
    }

    /** A2：母子页面可访问、链接未失效 */
    @Test
    void a2_fails_whenSubLinkMissing() {
        DeliveryItem item = new DeliveryItem("di_a2", tenant, "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(new ApprovedItemSnapshot("snap_a2", "di_a2", 1, 1, 1, "1.x",
                "p_1", "img_1", null, List.of("p_1"), 10_000, "RESERVED", List.of("image")));
        repository.save(item);
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.A);
        assertFalse(report.pass());
        assertFalse(check(report, "A2_PAGE_ACCESSIBLE").pass());
        assertTrue(check(report, "A4_QUOTA_VALID").pass(), "其余项不受影响");
    }

    /** A3：素材版本与申报一致 */
    @Test
    void a3_fails_whenImageMissing() {
        DeliveryItem item = new DeliveryItem("di_a3", tenant, null, "p_1", "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(snapshot("di_a3", 1, 10_000, List.of("image")));
        repository.save(item);
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.A);
        assertFalse(report.pass());
        assertFalse(check(report, "A3_IMAGE_CONSISTENT").pass());
        assertTrue(check(report, "A1_PRODUCT_ON_SALE").pass(), "其余项不受影响");
    }

    /** A4：额度有效（预留 / 余额充足） */
    @Test
    void a4_fails_whenBudgetAmountZero() {
        DeliveryItem item = new DeliveryItem("di_a4", tenant, "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(snapshot("di_a4", 1, 0, List.of("image")));
        repository.save(item);
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.A);
        assertFalse(report.pass());
        assertFalse(check(report, "A4_QUOTA_VALID").pass());
        assertTrue(check(report, "A2_PAGE_ACCESSIBLE").pass(), "其余项不受影响");
    }

    /** A5：套餐能力开通且在有效期 */
    @Test
    void a5_fails_whenTenantPlanMissing() {
        DeliveryItem item = validItem();
        PreflightCheckService.Report report = service.execute(SkillContext.of("tenant_unknown"),
                new PreflightCheckService.Input(item.getDeliveryItemId(), PreflightCheckService.CheckSet.A));
        assertFalse(report.pass());
        assertFalse(check(report, "A5_CAPABILITY_EFFECTIVE").pass());
        assertTrue(check(report, "A1_PRODUCT_ON_SALE").pass(), "其余项不受影响");
    }

    /** A6：投放条件齐备（图 / 页 / 额度三者齐） */
    @Test
    void a6_fails_whenConditionsEmpty() {
        DeliveryItem item = new DeliveryItem("di_a6", tenant, "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(snapshot("di_a6", 1, 10_000, List.of()));
        repository.save(item);
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.A);
        assertFalse(report.pass());
        assertFalse(check(report, "A6_CONDITIONS_READY").pass());
        assertTrue(check(report, "A4_QUOTA_VALID").pass(), "其余项不受影响");
    }

    // ---- B 集（创建 / 启用 / 恢复前 = A + 授权项）----

    /** B1：投放项已批准且为最新版本（修订升版后旧快照失效） */
    @Test
    void b1_fails_whenSnapshotNotLatest() {
        DeliveryItem item = new DeliveryItem("di_b1", tenant, "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.PENDING);
        repository.save(item);
        // 走状态机修订升版：SUBMIT → REJECT → RESUBMIT（版本升至 2，旧批准快照失效）
        com.kuoge.agentos.delivery.DeliveryItemStateMachine sm =
                new com.kuoge.agentos.delivery.DeliveryItemStateMachine();
        sm.transit(item, com.kuoge.agentos.delivery.DeliveryItemAction.SUBMIT, null);
        sm.transit(item, com.kuoge.agentos.delivery.DeliveryItemAction.REJECT, "revise");
        sm.transit(item, com.kuoge.agentos.delivery.DeliveryItemAction.RESUBMIT, "revise");
        // 残留 v1 旧快照：装配要素仍在（A 集不受影响），但已非最新批准版本
        item.setApprovedSnapshot(snapshot("di_b1", 1, 10_000, List.of("image")));
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.B);
        assertFalse(report.pass());
        assertFalse(check(report, "B1_APPROVED_LATEST").pass());
        assertTrue(check(report, "A4_QUOTA_VALID").pass(), "装配项不受影响");
    }

    /** B2：预算授权有效、未撤销 */
    @Test
    void b2_fails_whenBudgetAuthorizationRevoked() {
        DeliveryItem item = validItem();
        store.setBudgetAuthorization(tenant, false);
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.B);
        assertFalse(report.pass());
        assertFalse(check(report, "B2_BUDGET_AUTHORIZED").pass());
        assertTrue(check(report, "B1_APPROVED_LATEST").pass(), "其余授权项不受影响");
    }

    /** B3：执行前五项授权重检通过（套餐有效且未撤权） */
    @Test
    void b3_fails_whenTenantPlanRevoked() {
        DeliveryItem item = validItem();
        store.saveTenantPlan(new TenantPlan(tenant, "PLAN_KA", 1,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(10), true));
        PreflightCheckService.Report report = run(item, PreflightCheckService.CheckSet.B);
        assertFalse(report.pass());
        assertFalse(check(report, "B3_RECHECK_PASSED").pass());
        assertTrue(check(report, "B2_BUDGET_AUTHORIZED").pass(), "其余授权项不受影响");
    }
}
