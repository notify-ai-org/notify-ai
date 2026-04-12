package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_registry")
@Data
public class TenantRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "db_url", nullable = false)
    private String dbUrl;

    @Column(name = "db_username", nullable = false)
    private String dbUsername;

    @Column(name = "db_password", nullable = false)
    private String dbPassword;

    @Column(name = "db_driver_class", nullable = false)
    private String dbDriverClass;

    @Column(name = "pool_max_size", nullable = false)
    private Integer poolMaxSize = 10;

    @Column(name = "pool_min_idle", nullable = false)
    private Integer poolMinIdle = 2;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "schema_version")
    private String schemaVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
