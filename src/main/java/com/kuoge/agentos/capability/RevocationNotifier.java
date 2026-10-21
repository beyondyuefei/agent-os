package com.kuoge.agentos.capability;

/**
 * 撤权 / 到期事件通知（mock：记录通知，真实实现接飞书等通道）。
 */
public interface RevocationNotifier {

    void notifyOwner(String tenantId, String reason);
}
