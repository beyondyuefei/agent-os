package com.kuoge.agentos.channel;

/**
 * 创建状态反查结果（方案 5 AdChannelAdapter.getCreationStatus）。
 */
public enum CreationStatus {
    /** 反查确认已创建（仅证明已创建，不证明已启用） */
    CREATED,
    /** 反查确认未创建 */
    NOT_CREATED,
    /** 仍未知，继续反查 */
    STILL_UNKNOWN
}
