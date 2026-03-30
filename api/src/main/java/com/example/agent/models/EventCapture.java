package com.example.agent.models;

import com.example.agent.models.dto.RuleResultDto;
import com.example.agent.models.dto.SubjectResultDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.agent.models.deserializers.FlatteningMapDeserializer;
import java.util.Map;

@Entity
@Table(name = "event_capture")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCapture extends RawLog {

    @Transient
    @JsonDeserialize(using = FlatteningMapDeserializer.class)
    private Map<String, Object> payload;

    @ManyToOne(cascade = CascadeType.PERSIST)
    private Event event = new Event();

    // Execution data
    @Embedded
    private CallStack callStack; // captured stack frames

    @Embedded
    private ExecutionResult result; // success / failure

    @Embedded
    private ExceptionInfo exception; // if failed

    // Performance
    private long durationMillis; // execution time

    private String serviceName; // service name

    // Agent outputs
    @Column(columnDefinition = "TEXT")
    private String agentThoughtProcess;

    // We store the bullet points as a single comma-separated text string to natively match SQL rows
    @Column(columnDefinition = "TEXT")
    private String bulletReasons;

    /** Lifecycle status of this capture. */
    @Enumerated(EnumType.STRING)
    private CaptureStatus status;

    // Subject and Rule execution results
    @Transient
    private SubjectResultDto subjectResult; // result from subject supplier

    @Transient
    private List<RuleResultDto> ruleResults; // results from rule executions

    public Instant getOccuredAt() {
        return getTimestamp();
    }

    public void setOccuredAt(Instant occuredAt) {
        setTimestamp(occuredAt);
    }
}