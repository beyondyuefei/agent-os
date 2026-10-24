package com.kuoge.agentos.delivery;

/**
 * 预算台账（方案 3.3 ① / 验收规则 6）：
 * 预算总账恒等式任意时刻成立 —— 已花 + 占用 + 预留 + 余额 = 授权总额。
 * 系统台账与 Meta 实际花费每日对账，差异挂账不抹平。
 *
 * <p>实现：InMemoryBudgetLedger（synchronized 保恒等式）/
 * JpaBudgetLedger（@Transactional + 悲观锁保恒等式）。
 */
public interface BudgetLedger {

    /** 租户台账账户（五元组快照） */
    class Account {
        long authorized;   // 授权总额（分）
        long reserved;     // 预留
        long occupied;     // 占用
        long spent;        // 已花
        long discrepancy;  // 对账差异挂账

        public Account(long authorized) {
            this.authorized = authorized;
        }

        /** 持久层重建用：五元组全量还原 */
        public Account(long authorized, long reserved, long occupied, long spent, long discrepancy) {
            this.authorized = authorized;
            this.reserved = reserved;
            this.occupied = occupied;
            this.spent = spent;
            this.discrepancy = discrepancy;
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

    void openAccount(String tenantId, long authorizedAmount);

    Account account(String tenantId);

    /** 额度预留（组装时） */
    boolean reserve(String tenantId, long amount);

    /** 预留转占用（方案 3.3 ① 工作流第 4 步） */
    void occupyFromReserved(String tenantId, long amount);

    /** 从未分配余额直接占用 */
    boolean occupyUnallocated(String tenantId, long amount);

    /** 平台结算入账：占用转已花 */
    void settle(String tenantId, long amount);

    /** 对账：平台实际花费与系统台账差异挂账，不抹平 */
    void reconcile(String tenantId, long platformSpent);

    /** 全量断言恒等式（对账任务调用） */
    boolean assertInvariant(String tenantId);
}
