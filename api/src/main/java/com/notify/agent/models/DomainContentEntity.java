package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Domain content per client: vocabulary, rules, or other domain blob
 * loaded into AgentContext for the agent.
 */
@Entity
@Table(name = "domain_content", indexes = {
    @Index(name = "idx_domain_content_client_type", columnList = "clientId, type", unique = true)
})
@Data
public class DomainContentEntity {

    public enum Type { VOCABULARY, RULES, FULL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 256)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Type type;

    @Column(columnDefinition = "TEXT")
    private String contentJson;

    @Column(length = 32)
    private String version;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() { updatedAt = Instant.now(); }

}
