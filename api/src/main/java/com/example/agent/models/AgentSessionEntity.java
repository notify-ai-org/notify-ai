package com.example.agent.models;

import jakarta.persistence.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.google.adk.sessions.Session;

/**
 * Persisted agent session: conversation/session history per client and user.
 * Mapped to ADK Session (id, state) for runner.runAsync(userId, session.id(),
 * prompt).
 */
@Entity
@Table(name = "agent_sessions", indexes = {
        @Index(name = "idx_agent_sessions_client", columnList = "clientId"),
        @Index(name = "idx_agent_sessions_session_id", columnList = "sessionId", unique = true)
})
public class AgentSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** ADK session id (session.id()); unique. */
    @Column(nullable = false, unique = true, length = 256)
    private String sessionId;

    @Column(nullable = false, length = 256)
    private String clientId;

    @Column(length = 256)
    private String userId;

    /** Scopes from JWT, stored for audit. */
    @Column(length = 512)
    private String scope;

    /**
     * Structured session events — one row per ADK event for efficient
     * interval-based retrieval.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<SessionEventEntity> sessionEvents = new java.util.ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null)
            createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Session toSession() throws Exception {
        List<com.google.adk.events.Event> events = new java.util.ArrayList<>(sessionEvents.stream().map(session -> {
            try {
                return session.toAdkEvent();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }).filter(Objects::nonNull).toList());
        return Session.builder(id)
                .appName(clientId)
                .userId(userId)
                .events(events)
                .build();
    }

    // --- getters/setters ---
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
