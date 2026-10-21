package com.kuoge.agentos.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "task_execution")
@Data
public class TaskExecutionEntity {

    @Id
    @Column(length = 64)
    private String taskId;

    @Column(length = 64, nullable = false)
    private String workflowId;

    @Column(length = 64, nullable = false)
    private String tenantId;

    @Column(length = 64)
    private String agentType;

    @Column(length = 32)
    private String status;

    @Column(length = 2048)
    private String instruction;

    @Column(length = 2048)
    private String output;

    @Column(length = 2048)
    private String errorMessage;

    private Long latencyMs;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
