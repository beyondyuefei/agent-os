package com.kuoge.agentos.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "workflow_instance")
@Data
public class WorkflowInstanceEntity {

    @Id
    @Column(length = 64)
    private String workflowId;

    @Column(length = 64, nullable = false)
    private String tenantId;

    @Column(length = 64)
    private String userId;

    @Column(length = 64)
    private String productSku;

    @Column(length = 255)
    private String productName;

    @Column(length = 64)
    private String targetMarket;

    @Column(length = 32)
    private String status;

    @Column(length = 1024)
    private String errorMessage;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
