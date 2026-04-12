package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity to store registered client applications.
 */
@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_client_id", columnList = "clientId", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ClientEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 256)
    private String clientId;

    @Column(nullable = false, length = 256)
    private String applicationName;

    @Column(length = 256)
    private String basePackage;

    /** Optional secret for enhanced security (future use) */
    @Column(length = 512)
    private String clientSecret;

    @Column(name = "expires_at")
    private java.time.LocalDateTime expiresAt;

    @Column(name = "status")
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, REVOKED

    public boolean isExpired() {
        return expiresAt != null && java.time.LocalDateTime.now().isAfter(expiresAt);
    }
}
