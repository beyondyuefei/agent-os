package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.CapabilityInvokeLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapabilityInvokeLogRepository extends JpaRepository<CapabilityInvokeLogEntity, Long> {

    List<CapabilityInvokeLogEntity> findByTenantIdOrderById(String tenantId);
}
