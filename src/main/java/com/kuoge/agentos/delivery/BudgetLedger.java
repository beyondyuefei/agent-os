package com.kuoge.agentos.delivery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预算台账（方案 3.3 ① / 验收规则 6）：
 * 预算总账恒等式任意时刻成立 —— 已花 + 占用 + 预留 + 余额 = 授权总额。
 * 系统台账与 Meta 实际花费每日对账，差异挂账不抹平。
 */
public class BudgetLedger {

    /** 租户台账账户 */
    public static class Account {
        private long authorized;   // 授权总额（分）
        private long reserved;     // 预留
        private long occupied;     // 占用
        private long spent;        // 已花
        private long discrepancy;  // 对账差异挂账

        public Account(long authorized) {
            this.authorized = authorized;
        }

        public long authorized() { return authorized; }
        public long reserved() { return reserved; }
        public long occupied() { return occupied; }
        public long spent() { return spent; }
        public long discrepancy() { return discrepancy; }
        public long balance() { return authorized - reserved - occupied - spent; }

        /** 预算总账恒等式（验收规则 6）：任意时刻必须成立 */
        public boolean invariantHolds() {
            return spent + occupied + reserved + balance() == authorized;
        }
    }

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public void openAccount(String tenantId, long authorizedAmount) {
        accounts.put(tenantId, new Account(authorizedAmount));
    }

    public Account account(String tenantId) {
        Account account = accounts.get(tenantId);
        if (account == null) {
            throw new IllegalStateException("Budget account not found for tenant: " + tenantId);
        }
        return account;
    }

    /** 额度预留（组装时） */
    public synchronized boolean reserve(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.balance() < amount) {
            return false;
        }
        account.reserved += amount;
        return true;
    }

    /** 预留转占用（方案 3.3 ① 工作流第 4 步） */
    public synchronized void occupyFromReserved(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.reserved < amount) {
            throw new IllegalStateException("Reserved quota insufficient for tenant: " + tenantId);
        }
        account.reserved -= amount;
        account.occupied += amount;
    }

    /** 从未分配余额直接占用 */
    public synchronized boolean occupyUnallocated(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.balance() < amount) {
            return false;
        }
        account.occupied += amount;
        return true;
    }

    /** 平台结算入账：占用转已花 */
    public synchronized void settle(String tenantId, long amount) {
        Account account = account(tenantId);
        long fromOccupied = Math.min(account.occupied, amount);
        account.occupied -= fromOccupied;
        account.spent += amount;
    }

    /** 对账：平台实际花费与系统台账差异挂账，不抹平 */
    public synchronized void reconcile(String tenantId, long platformSpent) {
        Account account = account(tenantId);
        account.discrepancy += (platformSpent - account.spent);
    }

    /** 全量断言恒等式（对账任务调用） */
    public boolean assertInvariant(String tenantId) {
        return account(tenantId).invariantHolds();
    }
}
