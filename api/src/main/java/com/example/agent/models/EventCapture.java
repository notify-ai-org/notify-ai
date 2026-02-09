package com.example.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "event_capture")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCapture extends RawLog {

    @Transient
    private Vocabulary payload;

    @ManyToOne
    private Event event;

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

    @Deprecated
    public Instant getOccuredAt() {
        return getTimestamp();
    }

    @Deprecated
    public void setOccuredAt(Instant occuredAt) {
        setTimestamp(occuredAt);
    }
}

@Embeddable
@Data
class CallStack {
    @ElementCollection
    @CollectionTable(name = "call_stack_frames", joinColumns = @JoinColumn(name = "event_id"))
    private List<StackFrame> frames;
}

@Embeddable
@Data
class StackFrame {
    private String className; // "com.example.OrderService"
    private String methodName; // "createOrder"
    private int lineNumber; // line number in code
    private String fileName; // source file
}

@Embeddable
@Data
class ExecutionResult {
    private boolean success;
    @Column(length = 5000)
    private String returnValue; // serialized return value
}

@Embeddable
@Data
class ExceptionInfo {
    private String exceptionType; // java.lang.NullPointerException
    @Column(length = 1000)
    private String message; // exception message
    @Column(length = 10000)
    private String stackTrace; // full stack trace as string
}