package com.notify.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Rule {
    @Id
    private String id;

    private String name;

    private String eventName;         // Event this rule applies to

    private String description;

    private String conditionExpr;     // SpEL, MVEL, SQL-like DSL, etc.
    
    private boolean enabled;
    
    private int priority;             // resolve conflicts, tie-breaker

}
