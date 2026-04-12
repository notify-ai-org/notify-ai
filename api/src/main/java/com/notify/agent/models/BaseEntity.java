package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Base entity class providing common fields for all domain entities.
 * Includes validation flags for human-in-the-loop approval and audit
 * timestamps.
 */
@MappedSuperclass
@Data
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Audit fields
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Multi-tenancy
    private String tenantId;

    // Validation fields for human-in-the-loop
    @Column(nullable = false)
    private boolean validated = false;

    private Instant validatedAt;

    private String validatedBy;

    // Correlation for distributed tracing
    private String correlationId;

    /**
     * Marks this entity as validated by a human reviewer.
     * 
     * @param validatedBy The username or identifier of the validator
     */
    public void markAsValidated(String validatedBy) {
        this.validated = true;
        this.validatedAt = Instant.now();
        this.validatedBy = validatedBy;
    }

    /**
     * Revokes validation status (e.g., if entity is modified and needs
     * re-approval).
     */
    public void revokeValidation() {
        this.validated = false;
        this.validatedAt = null;
        this.validatedBy = null;
    }
}
