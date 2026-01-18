package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Rule {
    @Id
    private String id;

    private String name;

    private String eventName;         // Event this rule applies to

    private String description;

    private String conditionExpr;     // SpEL, MVEL, SQL-like DSL, etc.
    
    private boolean enabled;
    
    private int priority;             // resolve conflicts, tie-breaker

    // Constructors
    public Rule() {}

    public Rule(String id, String name, String eventName, String description, String conditionExpr, boolean enabled, int priority) {
        this.id = id;
        this.name = name;
        this.eventName = eventName;
        this.description = description;
        this.conditionExpr = conditionExpr;
        this.enabled = enabled;
        this.priority = priority;
    }

    // Getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
