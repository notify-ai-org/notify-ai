package com.example.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Common base for all raw telemetry/log entries.
 */
@MappedSuperclass
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class RawLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Instant timestamp;

    /** Multi-tenancy identifier. */
    private String tenantId;

    /** Link logs across various execution stages. */
    private String correlationId;

    /**
     * Processing lifecycle for the LogToMemoryAgentWorker.
     * <ul>
     *   <li>PENDING     – not yet picked up</li>
     *   <li>PROCESSING  – claimed by a worker; reset to PENDING on restart after crash</li>
     *   <li>PROCESSED   – successfully consumed and facts extracted</li>
     *   <li>FAILED      – extraction failed; excluded from future batches</li>
     * </ul>
     */
    public enum ProcessingStatus {
        PENDING, PROCESSING, PROCESSED, FAILED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "processingStatus", columnDefinition = "VARCHAR(16) DEFAULT 'PENDING'")
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    /**
     * Legacy column kept so tables created before the processingStatus migration don't reject
     * inserts with "Field 'processed' doesn't have a default value".
     * Hibernate now supplies {@code false} explicitly on every insert.
     * Once all tables are recreated via ddl-auto=create, this field will be removed.
     */
    @Column(name = "processed", columnDefinition = "boolean default false")
    private boolean processed = false;

    /** When the log was marked as PROCESSED or FAILED. */
    private Instant processedAt;

    // --- convenience helpers ---

    /** @return true only when this log has been successfully processed. */
    public boolean isProcessed() {
        return processingStatus == ProcessingStatus.PROCESSED;
    }

    /**
     * Drives the processingStatus field; also keeps the legacy {@code processed}
     * column in sync for backward compatibility.
     */
    public void setProcessed(boolean value) {
        this.processed = value;
        this.processingStatus = value ? ProcessingStatus.PROCESSED : ProcessingStatus.PENDING;
    }
}
