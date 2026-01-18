package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

import lombok.Data;

@Entity
@Data
public class EventSchedule {

    @Id
    private String id;

    private String eventName;

    private String description;

    private Instant scheduledAt; // exact time (if delayed)

    private String cronExpression; // for recurring

}
