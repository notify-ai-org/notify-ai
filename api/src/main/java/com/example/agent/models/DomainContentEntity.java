package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Domain content per client: vocabulary, rules, or other domain blob
 * loaded into AgentContext for the agent.
 */
@Entity
@Table(name = "domain_content", indexes = {
    @Index(name = "idx_domain_content_client_type", columnList = "clientId, type", unique = true)
})
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
