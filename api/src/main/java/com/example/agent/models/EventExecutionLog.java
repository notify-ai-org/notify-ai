package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
public class EventExecutionLog {
    
    @Id private String id;
    private String eventId;
    private String ruleId;
    private boolean matched;          // condition passed?
    private String evaluatedCondition;// actual evaluation result/expr trace
    private Instant executedAt;
    private String notes;             // debugging, overrides
}
