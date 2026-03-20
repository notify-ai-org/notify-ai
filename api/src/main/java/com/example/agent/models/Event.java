package com.example.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Event extends BaseEntity {

    private String name; // e.g. "ORDER_PLACED", "PAYMENT_FAILED"

    private String description; // event description

    private int priority; // resolve conflicts, tie-breaker

    @Enumerated(EnumType.STRING)
    private EventStatus status; // NEW, PROCESSED, FAILED

    private String scheduleIntent;

    private String preferredTimeWindow;

    private String eventType;

    // One-to-many relationship with EventCapture
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EventCapture> eventCaptures;

    // Constructors
    public enum EventStatus {
        NEW,
        PROCESSED,
        FAILED
    }

}
