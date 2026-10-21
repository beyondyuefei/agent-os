package com.kuoge.agentos.infrastructure.persistence.repository;

import com.kuoge.agentos.infrastructure.persistence.entity.TaskExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {

    List<TaskExecutionEntity> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
