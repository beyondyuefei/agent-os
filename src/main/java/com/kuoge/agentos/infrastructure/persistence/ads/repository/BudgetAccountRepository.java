package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.BudgetAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetAccountRepository extends JpaRepository<BudgetAccountEntity, String> {

    /** 写路径先取悲观写锁，替代内存实现的 synchronized，保住台账恒等式 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BudgetAccountEntity a where a.tenantId = :tenantId")
    Optional<BudgetAccountEntity> lockByTenantId(@Param("tenantId") String tenantId);
}
