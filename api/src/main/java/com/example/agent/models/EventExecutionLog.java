package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class EventExecutionLog {
    
    @Id private String id;
    private String eventId;
    private String ruleId;
    private boolean matched;          // condition passed?
    private String evaluatedCondition;// actual evaluation result/expr trace
    private Instant executedAt;
    private String notes;             // debugging, overrides

    // Constructors
    public EventExecutionLog() {}

    public EventExecutionLog(String id, String eventId, String ruleId, boolean matched, 
        String evaluatedCondition, Instant executedAt, String engineVersion, String notes) {
        this.id = id;
        this.eventId = eventId;
        this.ruleId = ruleId;
        this.matched = matched;
        this.evaluatedCondition = evaluatedCondition;
        this.executedAt = executedAt;
        this.notes = notes;
    }

    // Getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public String getEvaluatedCondition() { return evaluatedCondition; }
    public void setEvaluatedCondition(String evaluatedCondition) { this.evaluatedCondition = evaluatedCondition; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
