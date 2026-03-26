package com.example.agent.models;

import jakarta.persistence.*;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;

/**
 * Stores a single ADK {@link com.google.adk.events.Event} as a structured row.
 * Related to AgentSessionEntity via @ManyToOne so that event history can be
 * queried efficiently by sessionId and time interval.
 */
@Entity
@Table(name = "session_events", indexes = {
        @Index(name = "idx_session_events_session_id", columnList = "session_id"),
        @Index(name = "idx_session_events_occurred_at", columnList = "occurredAt")
})
public class SessionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Parent session. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSessionEntity session;

    /** ADK invocationId — ties this event to a specific agent run. */
    @Column(length = 256)
    private String invocationId;

    /** Who produced this event: "user", model name, or tool name. */
    @Column(length = 256)
    private String author;

    /** Conversation role: "user" or "model". */
    @Column(length = 64)
    private String role;

    /** First text part of the event, truncated to 2000 chars for fast preview. */
    @Column(columnDefinition = "TEXT")
    private String textContent;

    /** When the event occurred — used for interval-based retrieval. */
    @Column(nullable = false)
    private Instant occurredAt;

    /** Full serialised ADK Event JSON for replay or detailed inspection. */
    @Column(columnDefinition = "TEXT")
    private String rawJson;

    /**
     * Reconstructs the ADK {@link Event} from the stored {@link #rawJson}.
     *
     * @throws IOException if {@code rawJson} is null, blank, or cannot be deserialized.
     */
    public Event toAdkEvent() throws IOException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IOException("Cannot reconstruct ADK Event: rawJson is empty for SessionEventEntity id=" + id);
        }
        return new ObjectMapper().readValue(rawJson, Event.class);
    }

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public AgentSessionEntity getSession() { return session; }
    public void setSession(AgentSessionEntity session) { this.session = session; }

    public String getInvocationId() { return invocationId; }
    public void setInvocationId(String invocationId) { this.invocationId = invocationId; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
}
