package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.DeliveryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryItemJpaRepository extends JpaRepository<DeliveryItemEntity, String> {

    List<DeliveryItemEntity> findByTenantId(String tenantId);
}
