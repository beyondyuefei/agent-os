package com.kuoge.agentos.infrastructure.persistence.repository;

import com.kuoge.agentos.infrastructure.persistence.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, String> {

    List<WorkflowInstanceEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
