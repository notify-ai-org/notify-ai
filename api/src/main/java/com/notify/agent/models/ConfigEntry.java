package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single configuration key-value pair stored in the database.
 * The {@code ManagedConfigService} reads these entries and applies them
 * to fields annotated with {@code @ManagedConfiguration}.
 */
@Entity
@Table(name = "config_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigEntry {

    /** Config key, e.g. "agent.orchestrator.core-pool-size". */
    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    /**
     * Serialised value (always stored as a string; coerced by the config service).
     */
    @Column(name = "config_value", nullable = false, length = 4096)
    private String configValue;

    /** Human-readable description of what this config controls. */
    @Column(length = 1024)
    private String description;

    /** When this entry was last updated. */
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
