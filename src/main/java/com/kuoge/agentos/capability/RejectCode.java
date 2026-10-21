package com.kuoge.agentos.capability;

/**
 * 网关拒绝码（方案 2.2 判定矩阵 / 8.1）。
 */
public enum RejectCode {

    /** 能力未开通 */
    ENT_001,
    /** 套餐到期 / 已撤权 */
    AUTH_002,
    /** 操作者权限不足 */
    AUTH_003,
    /** 投放项未批准 / 批准版本失效 */
    APR_005,
    /** 预算授权失效 */
    BUD_004,
    /** 数据越权 */
    DAT_006
}
