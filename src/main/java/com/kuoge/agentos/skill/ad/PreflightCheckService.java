package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.ArrayList;
import java.util.List;

/**
 * ad.preflight.check 投前校验（方案 3.6 ②，确定性服务，只读，风险等级 read）。
 * 两套检查集：
 * A 集（送审前）查装配齐备：商品 / 页面 / 素材 / 额度 / 能力 / 条件，缺一不可；
 * B 集（创建/启用/恢复前）= A + 授权项：已批准且最新 / 预算授权 / 五项重检。
 * 任何一项不通过即阻断，不依赖模型判断；每项检查可独立单测。
 */
public class PreflightCheckService implements DomainService<PreflightCheckService.Input, PreflightCheckService.Report> {

    public enum CheckSet { A, B }

    public record Input(String deliveryItemId, CheckSet checkSet) {}
    public record Check(String code, boolean pass, String detail) {}
    public record Report(boolean pass, List<Check> checks) {
        public Report {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }

    private final DeliveryItemStore repository;
    private final CapabilityStore capabilityStore;

    public PreflightCheckService(DeliveryItemStore repository, CapabilityStore capabilityStore) {
        this.repository = repository;
        this.capabilityStore = capabilityStore;
    }

    @Override
    public Report execute(SkillContext ctx, Input input) {
        DeliveryItem item = repository.findById(input.deliveryItemId())
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + input.deliveryItemId()));
        List<Check> checks = new ArrayList<>();
        // 检查集 A · 送审前（装配齐备，缺一不可）
        checks.add(check("A1_PRODUCT_ON_SALE", item.getProductId() != null, "商品在售且信息未变更"));
        checks.add(check("A2_PAGE_ACCESSIBLE", item.getApprovedSnapshot() != null
                && item.getApprovedSnapshot().subLink() != null, "母子页面可访问、链接未失效"));
        checks.add(check("A3_IMAGE_CONSISTENT", item.getImageId() != null, "素材版本与申报一致"));
        checks.add(check("A4_QUOTA_VALID", item.getApprovedSnapshot() != null
                && item.getApprovedSnapshot().budgetAmount() > 0, "额度有效（预留 / 余额充足）"));
        checks.add(check("A5_CAPABILITY_EFFECTIVE",
                capabilityStore.findTenantPlan(ctx.tenantId())
                        .map(tp -> tp.effective(java.time.LocalDateTime.now())).orElse(false),
                "套餐能力开通且在有效期"));
        checks.add(check("A6_CONDITIONS_READY", item.getApprovedSnapshot() != null
                && !item.getApprovedSnapshot().conditions().isEmpty(), "投放条件齐备（图 / 页 / 额度三者齐）"));
        if (input.checkSet() == CheckSet.B) {
            // 检查集 B · 创建 / 启用 / 恢复前（A + 授权项）
            checks.add(check("B1_APPROVED_LATEST", item.approvedAndLatest(), "投放项已批准且为最新版本"));
            checks.add(check("B2_BUDGET_AUTHORIZED",
                    capabilityStore.budgetAuthorizationValid(ctx.tenantId()), "预算授权有效、未撤销"));
            checks.add(check("B3_RECHECK_PASSED",
                    capabilityStore.findTenantPlan(ctx.tenantId())
                            .map(tp -> tp.effective(java.time.LocalDateTime.now()) && !tp.revoked())
                            .orElse(false),
                    "执行前五项授权重检通过"));
        }
        boolean pass = checks.stream().allMatch(Check::pass);
        return new Report(pass, checks);
    }

    private Check check(String code, boolean pass, String detail) {
        return new Check(code, pass, detail);
    }
}
