package com.example.agent.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "event_capture")
@Data
public class EventCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Instant occuredAt; // when the event was captured

    @Transient
    private Vocabulary payload;

    @ManyToOne
    private Event event;
  
    // Execution data
    @Embedded
    private CallStack callStack;             // captured stack frames
    
    @Embedded
    private ExecutionResult result;          // success / failure
    
    @Embedded
    private ExceptionInfo exception;         // if failed

    // Performance
    private long durationMillis;             // execution time


    private String serviceName;              // service name

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
    private String className;                // "com.example.OrderService"
    private String methodName;               // "createOrder"
    private int lineNumber;                  // line number in code
    private String fileName;                 // source file
}

@Embeddable
@Data
class ExecutionResult {
    private boolean success;
    @Column(length = 5000)
    private String returnValue;              // serialized return value
}

@Embeddable
@Data
class ExceptionInfo {
    private String exceptionType;            // java.lang.NullPointerException
    @Column(length = 1000)
    private String message;                  // exception message
    @Column(length = 10000)
    private String stackTrace;               // full stack trace as string
}