package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ad.item.assemble 投放项组装（方案 3.3 ①，确定性服务，风险等级 write-low-risk）。
 * 工作流：读计划与母集合规则 → 生成排序子链接（本图商品置前）→ 链接可用性检查
 * → 额度预留转占用 → 组装批准快照 → 落库并推送投放确认工作台。
 * 降级：母链接未就绪 → 该项转待补（不阻塞其他图）。
 */
public class ItemAssembleService implements DomainService<ItemAssembleService.Input, DeliveryItem> {

    /** 母集合就绪状态提供方（mock 注入） */
    public interface CollectionReadiness {
        boolean ready(String collectionId);
    }

    public record Input(String imageId, String productId, String planId,
                        String collectionId, long budgetAmount) {}

    private final DeliveryItemRepository repository;
    private final BudgetLedger budgetLedger;
    private final CollectionReadiness collectionReadiness;
    private final AtomicInteger idSequence = new AtomicInteger(0);

    public ItemAssembleService(DeliveryItemRepository repository, BudgetLedger budgetLedger,
                               CollectionReadiness collectionReadiness) {
        this.repository = repository;
        this.budgetLedger = budgetLedger;
        this.collectionReadiness = collectionReadiness;
    }

    @Override
    public DeliveryItem execute(SkillContext ctx, Input input) {
        String itemId = "di_" + idSequence.incrementAndGet();
        if (!collectionReadiness.ready(input.collectionId())) {
            // 母链接未就绪 → 待补，不阻塞其他图
            DeliveryItem pending = new DeliveryItem(itemId, ctx.tenantId(), input.imageId(),
                    input.productId(), input.planId(), input.collectionId(), DeliveryItemStatus.PENDING);
            return repository.save(pending);
        }
        // 额度预留 → 占用（只读本项预留与真实未分配余额）
        if (!budgetLedger.reserve(ctx.tenantId(), input.budgetAmount())) {
            budgetLedger.occupyUnallocated(ctx.tenantId(), input.budgetAmount());
        } else {
            budgetLedger.occupyFromReserved(ctx.tenantId(), input.budgetAmount());
        }
        // 生成排序子链接：本图商品置前
        String subLink = "https://shop.example.com/c/" + input.collectionId()
                + "?featured=" + input.productId();
        ApprovedItemSnapshot snapshot = new ApprovedItemSnapshot(
                "snap_" + itemId + "_v1", itemId, 1,
                1, 1, "1.x",
                input.productId(), input.imageId(), subLink,
                List.of(input.productId()),
                input.budgetAmount(), "RESERVED",
                List.of("image", "page", "quota"));
        DeliveryItem item = new DeliveryItem(itemId, ctx.tenantId(), input.imageId(),
                input.productId(), input.planId(), input.collectionId(),
                DeliveryItemStatus.PENDING);
        item.setApprovedSnapshot(snapshot);
        return repository.save(item);
    }
}
