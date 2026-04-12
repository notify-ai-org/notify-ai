package com.notify.agent.models;

import jakarta.persistence.Entity;
import java.time.Instant;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class EventSchedule extends BaseEntity {

    private String eventName;

    private String description;

    private String triggerType;

    private Instant scheduledAt; // exact time (if delayed)

    private String cronExpression; // for recurring

}
