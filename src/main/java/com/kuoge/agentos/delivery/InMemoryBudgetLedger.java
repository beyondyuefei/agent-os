package com.kuoge.agentos.delivery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预算台账内存实现：synchronized 方法保证恒等式（语义与原 BudgetLedger 完全一致）。
 */
public class InMemoryBudgetLedger implements BudgetLedger {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void openAccount(String tenantId, long authorizedAmount) {
        accounts.put(tenantId, new Account(authorizedAmount));
    }

    @Override
    public Account account(String tenantId) {
        Account account = accounts.get(tenantId);
        if (account == null) {
            throw new IllegalStateException("Budget account not found for tenant: " + tenantId);
        }
        return account;
    }

    /** 额度预留（组装时） */
    @Override
    public synchronized boolean reserve(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.balance() < amount) {
            return false;
        }
        account.reserved += amount;
        return true;
    }

    /** 预留转占用（方案 3.3 ① 工作流第 4 步） */
    @Override
    public synchronized void occupyFromReserved(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.reserved < amount) {
            throw new IllegalStateException("Reserved quota insufficient for tenant: " + tenantId);
        }
        account.reserved -= amount;
        account.occupied += amount;
    }

    /** 从未分配余额直接占用 */
    @Override
    public synchronized boolean occupyUnallocated(String tenantId, long amount) {
        Account account = account(tenantId);
        if (account.balance() < amount) {
            return false;
        }
        account.occupied += amount;
        return true;
    }

    /** 平台结算入账：占用转已花 */
    @Override
    public synchronized void settle(String tenantId, long amount) {
        Account account = account(tenantId);
        long fromOccupied = Math.min(account.occupied, amount);
        account.occupied -= fromOccupied;
        account.spent += amount;
    }

    /** 对账：平台实际花费与系统台账差异挂账，不抹平 */
    @Override
    public synchronized void reconcile(String tenantId, long platformSpent) {
        Account account = account(tenantId);
        account.discrepancy += (platformSpent - account.spent);
    }

    /** 全量断言恒等式（对账任务调用） */
    @Override
    public boolean assertInvariant(String tenantId) {
        return account(tenantId).invariantHolds();
    }
}
