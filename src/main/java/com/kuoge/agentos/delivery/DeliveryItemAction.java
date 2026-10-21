package com.kuoge.agentos.delivery;

/**
 * 投放项状态迁移动作（方案 6 状态迁移表）。
 */
public enum DeliveryItemAction {
    /** 组装完成送审：待补 → 待审（图/页/额度齐备） */
    SUBMIT,
    /** 负责人通过：待审 → 已批准 */
    APPROVE,
    /** 负责人退回：待审 → 已退回（记原因） */
    REJECT,
    /** 补齐重送：已退回 → 待审（修订产生新版本） */
    RESUBMIT,
    /** 自动创建开始：已批准 → 创建中（执行前五项重检 + 幂等键） */
    CREATE_START,
    /** 创建成功：创建中 → 已创建（平台回执 SUCCESS） */
    CREATE_SUCCESS,
    /** 创建明确失败：创建中 → 创建失败（平台错误码确认） */
    CREATE_FAIL,
    /** 创建状态未知：创建中 → 待查（超时 / 无回执） */
    CREATE_UNKNOWN,
    /** 反查确认已创建：待查 → 已创建 */
    CONFIRM_CREATED,
    /** 反查确认未创建：待查 → 创建失败 */
    CONFIRM_NOT_CREATED,
    /** 限次重试：创建失败 → 创建中（超限转人工） */
    RETRY_CREATE,
    /** 启用：已创建 → 运行中（风险动作，执行前五项重检） */
    ENABLE,
    /** 人工暂停：运行中 → 已暂停（停止类动作，放行） */
    PAUSE,
    /** 恢复投放：已暂停 → 运行中（RESUME = 风险动作，授权重检） */
    RESUME,
    /** 结束（额度/周期/人工）：运行中|已暂停 → 已结束（停止类） */
    END,
    /** 撤权关停：运行中|已暂停 → 已结束（停止类不受套餐阻断） */
    REVOKE_END,
    /** 整计划退回：已批准|待审 → 已取消 */
    CANCEL
}
