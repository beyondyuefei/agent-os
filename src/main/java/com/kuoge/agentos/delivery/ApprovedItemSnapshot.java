package com.kuoge.agentos.delivery;

import java.util.List;

/**
 * 批准快照（方案 2.4 approved_item_snapshot）：记录批准时的
 * plan / policy / skill 版本与完整内容，防配置漂移，供审计。不可变。
 */
public record ApprovedItemSnapshot(
        String snapshotId,
        String deliveryItemId,
        int itemVersion,
        int planVersion,
        int policyVersion,
        String skillVersion,
        /** 商品 */
        String productId,
        /** 素材图版本 */
        String imageId,
        /** 排序子链接（本图商品置前） */
        String subLink,
        /** 完整排序 */
        List<String> ordering,
        /** 预算占用金额（分） */
        long budgetAmount,
        /** 预算来源：RESERVED 预留 / UNALLOCATED 未分配 */
        String budgetSource,
        /** 投放条件 */
        List<String> conditions
) {

    public ApprovedItemSnapshot {
        ordering = ordering == null ? List.of() : List.copyOf(ordering);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
