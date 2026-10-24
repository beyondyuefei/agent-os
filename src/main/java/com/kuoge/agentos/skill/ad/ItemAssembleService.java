package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ad.item.assemble 投放项组装（方案 3.3 ①，确定性服务，风险等级 write-low-risk）。
 * 工作流：读计划与母集合规则 → 生成排序子链接（本图商品置前）→ 链接可用性检查
 * → 额度预留转占用 → 组装批准快照 → 落库并推送投放确认工作台。
 * 降级：母链接未就绪 → 该项转待补（不阻塞其他图），待母集合就绪后经 complete 补齐。
 */
public class ItemAssembleService implements DomainService<ItemAssembleService.Input, DeliveryItem> {

    /** 母集合就绪状态提供方（mock 注入） */
    public interface CollectionReadiness {
        boolean ready(String collectionId);
    }

    /** 额度不足：预留与未分配余额都无法覆盖申报预算 */
    public static class QuotaInsufficientException extends RuntimeException {
        public QuotaInsufficientException(String tenantId, long amount) {
            super("QUOTA_INSUFFICIENT: tenant=" + tenantId + " amount=" + amount);
        }
    }

    public record Input(String imageId, String productId, String planId,
                        String collectionId, long budgetAmount) {}

    private final DeliveryItemStore repository;
    private final BudgetLedger budgetLedger;
    private final CollectionReadiness collectionReadiness;
    private final AtomicInteger idSequence = new AtomicInteger(0);

    public ItemAssembleService(DeliveryItemStore repository, BudgetLedger budgetLedger,
                               CollectionReadiness collectionReadiness) {
        this.repository = repository;
        this.budgetLedger = budgetLedger;
        this.collectionReadiness = collectionReadiness;
    }

    @Override
    public DeliveryItem execute(SkillContext ctx, Input input) {
        String itemId = "di_" + idSequence.incrementAndGet();
        DeliveryItem item = new DeliveryItem(itemId, ctx.tenantId(), input.imageId(),
                input.productId(), input.planId(), input.collectionId(),
                DeliveryItemStatus.PENDING);
        item.setBudgetAmount(input.budgetAmount());
        if (!collectionReadiness.ready(input.collectionId())) {
            // 母链接未就绪 → 待补，不阻塞其他图
            return repository.save(item);
        }
        occupyBudget(ctx.tenantId(), input.budgetAmount());
        item.setApprovedSnapshot(buildSnapshot(item));
        return repository.save(item);
    }

    /**
     * 待补项补齐（方案 3.7 母集合就绪事件入口）：生成排序子链接 → 额度占用 → 组装批准快照。
     * 补齐后状态仍为 PENDING，等送审；已齐备（快照有效且最新）的项幂等返回。
     */
    public DeliveryItem complete(SkillContext ctx, String deliveryItemId) {
        DeliveryItem item = repository.findById(deliveryItemId)
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + deliveryItemId));
        if (item.getStatus() != DeliveryItemStatus.PENDING) {
            throw new IllegalStateException("COMPLETE_ONLY_FOR_PENDING: " + deliveryItemId
                    + " status=" + item.getStatus());
        }
        if (item.approvedAndLatest()) {
            return item;
        }
        occupyBudget(item.getTenantId(), item.getBudgetAmount());
        item.setApprovedSnapshot(buildSnapshot(item));
        return repository.save(item);
    }

    /** 额度预留 → 占用（只读本项预留与真实未分配余额）；两边都不够则额度不足 */
    private void occupyBudget(String tenantId, long amount) {
        if (budgetLedger.reserve(tenantId, amount)) {
            budgetLedger.occupyFromReserved(tenantId, amount);
            return;
        }
        if (!budgetLedger.occupyUnallocated(tenantId, amount)) {
            throw new QuotaInsufficientException(tenantId, amount);
        }
    }

    /** 组装批准快照：排序子链接本图商品置前，快照版本跟随投放项版本 */
    private ApprovedItemSnapshot buildSnapshot(DeliveryItem item) {
        String subLink = "https://shop.example.com/c/" + item.getCollectionId()
                + "?featured=" + item.getProductId();
        return new ApprovedItemSnapshot(
                "snap_" + item.getDeliveryItemId() + "_v" + item.getVersion(),
                item.getDeliveryItemId(), item.getVersion(),
                1, 1, "1.x",
                item.getProductId(), item.getImageId(), subLink,
                List.of(item.getProductId()),
                item.getBudgetAmount(), "RESERVED",
                List.of("image", "page", "quota"));
    }
}
