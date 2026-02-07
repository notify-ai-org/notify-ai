package com.example.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted "fact" extracted from raw event/notification logs.
 * Facts are compact, queryable statements that can be used as memory/context.
 */
@Entity
@Table(
    name = "facts",
    indexes = {
        @Index(name = "idx_facts_client_observed", columnList = "clientId, observedAt"),
        @Index(name = "idx_facts_type", columnList = "factType"),
        @Index(name = "idx_facts_correlation", columnList = "correlationId")
    }
)
public class FactEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 256)
    private String clientId;

    /** e.g. EVENT_LOG, NOTIFICATION_LOG, ENGINE, ACP */
    @Column(length = 64)
    private String sourceType;

    /** e.g. DELIVERY_FAILURE, USER_NOTIFIED, RULE_MATCHED */
    @Column(length = 128)
    private String factType;

    /**
     * Human-readable fact sentence, optimized for retrieval.
     * Example: "Order 123 was placed by user u1 for $49.99."
     */
    @Column(length = 2048)
    private String sentence;

    private Instant observedAt;

    private double confidence;

    private double importance;

    private int ttlDays;

    @Column(length = 256)
    private String correlationId;

    /** Raw evidence (JSON) for traceability. */
    @Column(columnDefinition = "TEXT")
    private String evidenceJson;

    /** Optional: reference to source event ids (CSV/JSON). */
    @Column(columnDefinition = "TEXT")
    private String sourceEventIdsJson;

    private Instant createdAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (observedAt == null) observedAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getSentence() { return sentence; }
    public void setSentence(String sentence) { this.sentence = sentence; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }
    public int getTtlDays() { return ttlDays; }
    public void setTtlDays(int ttlDays) { this.ttlDays = ttlDays; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getSourceEventIdsJson() { return sourceEventIdsJson; }
    public void setSourceEventIdsJson(String sourceEventIdsJson) { this.sourceEventIdsJson = sourceEventIdsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

