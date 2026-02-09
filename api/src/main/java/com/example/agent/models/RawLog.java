package com.example.agent.models;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Data;

/**
 * Common base for all raw telemetry/log entries.
 */
@MappedSuperclass
@Data
public abstract class RawLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Instant timestamp;

    /** Multi-tenancy identifier. */
    private String tenantId;

    /** Link logs across various execution stages. */
    private String correlationId;
}
