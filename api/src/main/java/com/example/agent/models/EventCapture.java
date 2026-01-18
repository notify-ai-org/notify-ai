package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "event_capture")
public class EventCapture {
    @Id
    private String eventName;                  // unique id for correlation
    
    private String eventType;                // e.g. "HTTP_REQUEST", "DB_QUERY", "SERVICE_CALL"
    private String eventDescription;

    private Instant occuredAt;               // when the event was captured
    private String scheduleIntent;

    private String preferredTimeWindow;

    // Raw payload (input data or message body)
    @Column(length = 10000)
    private String payload;
  
    // Execution data
    @Embedded
    private CallStack callStack;             // captured stack frames
    
    @Embedded
    private ExecutionResult result;          // success / failure
    
    @Embedded
    private ExceptionInfo exception;         // if failed

    // Performance
    private long durationMillis;             // execution time
    private String threadName;               // thread that executed
    private String serviceName;              // service name

    // Constructors
    public EventCapture() {}

    // Getters and setters
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public Instant getOccuredAt() { return occuredAt; }
    public void setOccuredAt(Instant occuredAt) { this.occuredAt = occuredAt; }
    
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public CallStack getCallStack() { return callStack; }
    public void setCallStack(CallStack callStack) { this.callStack = callStack; }
    
    public ExecutionResult getResult() { return result; }
    public void setResult(ExecutionResult result) { this.result = result; }
    
    public ExceptionInfo getException() { return exception; }
    public void setException(ExceptionInfo exception) { this.exception = exception; }
    
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    
    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEventDescription() {
        return eventDescription;
    }
    public void setEventDescription(String eventDescription) {
        this.eventDescription = eventDescription;
    }

    public String getScheduleIntent() {
        return scheduleIntent;
    }
    public void setScheduleIntent(String scheduleIntent) {
        this.scheduleIntent = scheduleIntent;
    }

    public String getPreferredTimeWindow() {
        return preferredTimeWindow;
    }
    public void setPreferredTimeWindow(String preferredTimeWindow) {
        this.preferredTimeWindow = preferredTimeWindow;
    }
             
}

@Embeddable
class CallStack {
    @ElementCollection
    @CollectionTable(name = "call_stack_frames", joinColumns = @JoinColumn(name = "event_id"))
    private List<StackFrame> frames;

    public List<StackFrame> getFrames() { return frames; }
    public void setFrames(List<StackFrame> frames) { this.frames = frames; }
}

@Embeddable
class StackFrame {
    private String className;                // "com.example.OrderService"
    private String methodName;               // "createOrder"
    private int lineNumber;                  // line number in code
    private String fileName;                 // source file

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}

@Embeddable
class ExecutionResult {
    private boolean success;
    @Column(length = 5000)
    private String returnValue;              // serialized return value

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getReturnValue() { return returnValue; }
    public void setReturnValue(String returnValue) { this.returnValue = returnValue; }
}

@Embeddable
class ExceptionInfo {
    private String exceptionType;            // java.lang.NullPointerException
    @Column(length = 1000)
    private String message;                  // exception message
    @Column(length = 10000)
    private String stackTrace;               // full stack trace as string

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
}