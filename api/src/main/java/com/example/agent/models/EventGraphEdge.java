package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;


@Entity
public class EventGraphEdge {
    @Id
    private String id;

    private Event fromEvent;
    private Event toEvent;

    private Rule conditionExpr; // e.g., "payment.status == SUCCESS"

}

