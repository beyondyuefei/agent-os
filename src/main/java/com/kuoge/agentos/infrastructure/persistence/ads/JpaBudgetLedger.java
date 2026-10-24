package com.kuoge.agentos.infrastructure.persistence.ads;

import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.infrastructure.persistence.ads.entity.BudgetAccountEntity;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAccountRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预算台账 JPA 实现（方案 3.3 ① budget_account 五元组）。
 * 并发语义：内存实现用 synchronized；这里用 @Transactional + 悲观写锁
 * （lockByTenantId = SELECT ... FOR UPDATE），读改写同事务，保住恒等式。
 */
public class JpaBudgetLedger implements BudgetLedger {

    private final BudgetAccountRepository repository;

    public JpaBudgetLedger(BudgetAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void openAccount(String tenantId, long authorizedAmount) {
        BudgetAccountEntity entity = repository.findById(tenantId)
                .orElseGet(BudgetAccountEntity::new);
        entity.setTenantId(tenantId);
        entity.setAuthorized(authorizedAmount);
        entity.setReserved(0);
        entity.setOccupied(0);
        entity.setSpent(0);
        entity.setDiscrepancy(0);
        repository.save(entity);
    }

    @Override
    public Account account(String tenantId) {
        return toAccount(load(tenantId));
    }

    /** 额度预留（组装时） */
    @Override
    @Transactional
    public boolean reserve(String tenantId, long amount) {
        BudgetAccountEntity entity = lockAccount(tenantId);
        long balance = entity.getAuthorized() - entity.getReserved()
                - entity.getOccupied() - entity.getSpent();
        if (balance < amount) {
            return false;
        }
        entity.setReserved(entity.getReserved() + amount);
        repository.save(entity);
        return true;
    }

    /** 预留转占用（方案 3.3 ① 工作流第 4 步） */
    @Override
    @Transactional
    public void occupyFromReserved(String tenantId, long amount) {
        BudgetAccountEntity entity = lockAccount(tenantId);
        if (entity.getReserved() < amount) {
            throw new IllegalStateException("Reserved quota insufficient for tenant: " + tenantId);
        }
        entity.setReserved(entity.getReserved() - amount);
        entity.setOccupied(entity.getOccupied() + amount);
        repository.save(entity);
    }

    /** 从未分配余额直接占用 */
    @Override
    @Transactional
    public boolean occupyUnallocated(String tenantId, long amount) {
        BudgetAccountEntity entity = lockAccount(tenantId);
        long balance = entity.getAuthorized() - entity.getReserved()
                - entity.getOccupied() - entity.getSpent();
        if (balance < amount) {
            return false;
        }
        entity.setOccupied(entity.getOccupied() + amount);
        repository.save(entity);
        return true;
    }

    /** 平台结算入账：占用转已花 */
    @Override
    @Transactional
    public void settle(String tenantId, long amount) {
        BudgetAccountEntity entity = lockAccount(tenantId);
        long fromOccupied = Math.min(entity.getOccupied(), amount);
        entity.setOccupied(entity.getOccupied() - fromOccupied);
        entity.setSpent(entity.getSpent() + amount);
        repository.save(entity);
    }

    /** 对账：平台实际花费与系统台账差异挂账，不抹平 */
    @Override
    @Transactional
    public void reconcile(String tenantId, long platformSpent) {
        BudgetAccountEntity entity = lockAccount(tenantId);
        entity.setDiscrepancy(entity.getDiscrepancy() + (platformSpent - entity.getSpent()));
        repository.save(entity);
    }

    /** 全量断言恒等式（对账任务调用） */
    @Override
    public boolean assertInvariant(String tenantId) {
        return account(tenantId).invariantHolds();
    }

    private BudgetAccountEntity load(String tenantId) {
        return repository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Budget account not found for tenant: " + tenantId));
    }

    private BudgetAccountEntity lockAccount(String tenantId) {
        return repository.lockByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Budget account not found for tenant: " + tenantId));
    }

    private static Account toAccount(BudgetAccountEntity e) {
        return new Account(e.getAuthorized(), e.getReserved(), e.getOccupied(),
                e.getSpent(), e.getDiscrepancy());
    }
}
