package com.example.agent.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name; // e.g. "ORDER_PLACED", "PAYMENT_FAILED"

    private String description; // event description

    private int priority; // resolve conflicts, tie-breaker

    @Enumerated(EnumType.STRING)
    private EventStatus status; // NEW, PROCESSED, FAILED

    // Optional correlation for deduplication/grouping
    private String correlationId;

    private String scheduleIntent;

    private String preferredTimeWindow;

    private String eventType;

    // One-to-many relationship with EventCapture
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EventCapture> eventCaptures;

    // Constructors
    public enum EventStatus {
        NEW,
        PROCESSED,
        FAILED
    }

}
