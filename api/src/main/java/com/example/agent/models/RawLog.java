package com.example.agent.models;

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

    /** Whether this log has been consumed by the LogToMemoryAgentWorker. */
    private boolean processed = false;

    /** When the log was marked as processed. */
    private Instant processedAt;
}
