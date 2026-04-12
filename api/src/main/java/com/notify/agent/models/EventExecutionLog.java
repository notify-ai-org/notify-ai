package com.notify.agent.models;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventExecutionLog extends RawLog {

    private String eventId;
    private String ruleId;
    private boolean matched; // condition passed?
    private String evaluatedCondition;// actual evaluation result/expr trace
    private String notes; // debugging, overrides

    @Deprecated
    public Instant getExecutedAt() {
        return getTimestamp();
    }

    @Deprecated
    public void setExecutedAt(Instant executedAt) {
        setTimestamp(executedAt);
    }
}
