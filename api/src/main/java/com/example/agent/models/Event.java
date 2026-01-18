package com.example.agent.models;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class Event {
    @Id private String id;

    private String name;              // e.g. "ORDER_PLACED", "PAYMENT_FAILED"

    private String description;       // event description
    
    private String payloadJson;       // raw JSON for extensibility

    private int priority;             // resolve conflicts, tie-breaker

    @Enumerated(EnumType.STRING)
    private EventStatus status;       // NEW, PROCESSED, FAILED


    // Constructors
    public Event() {}

    public Event(String id, String name, String description, String payloadJson,
     int priority, EventStatus status, String correlationId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.payloadJson = payloadJson;
        this.priority = priority;
        this.status = status;
        this.correlationId = correlationId;
    }

    // Getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public enum EventStatus {
        NEW,
        PROCESSED,
        FAILED
    }

    // Optional correlation for deduplication/grouping
    private String correlationId;

    // One-to-many relationship with EventCapture
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EventCapture> eventCaptures;

    public List<EventCapture> getEventCaptures() {
        return eventCaptures;
    }

    public void setEventCaptures(List<EventCapture> eventCaptures) {
        this.eventCaptures = eventCaptures;
    }
}

