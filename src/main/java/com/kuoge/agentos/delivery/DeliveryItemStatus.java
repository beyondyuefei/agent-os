package com.kuoge.agentos.delivery;

/**
 * 单图投放项状态（方案第六章状态机，P0）。
 */
public enum DeliveryItemStatus {
    /** 待补：素材通过但母集合未就绪 / 缺项 */
    PENDING,
    /** 待审：装配齐备，进入批准队列 */
    READY_FOR_REVIEW,
    /** 已批准：快照冻结 */
    APPROVED,
    /** 已退回：记原因，仅影响本项 */
    RETURNED,
    /** 创建中 */
    CREATING,
    /** 已创建（≠ 运行中，启用是独立风险动作） */
    CREATED,
    /** 创建失败 */
    CREATE_FAILED,
    /** 待查：创建状态未知，进入反查 */
    PENDING_CHECK,
    /** 运行中 */
    RUNNING,
    /** 已暂停 */
    PAUSED,
    /** 已结束 */
    ENDED,
    /** 已取消 */
    CANCELLED
}
