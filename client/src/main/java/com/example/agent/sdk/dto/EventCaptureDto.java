package com.example.agent.sdk.dto;

import java.time.Instant;

/**
 * DTO for event capture sent to acp-server POST /api/event.
 * Must match the structure expected by EventConsumer (eventName, eventType, occuredAt, payload required).
 */
public class EventCaptureDto {
    private String eventName;
    private String eventType;
    private String eventDescription;
    private Instant occuredAt;
    private String scheduleIntent;
    private String preferredTimeWindow;
    private String payload;
    private long durationMillis;
    private String threadName;
    private String serviceName;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }
    public Instant getOccuredAt() { return occuredAt; }
    public void setOccuredAt(Instant occuredAt) { this.occuredAt = occuredAt; }
    public String getScheduleIntent() { return scheduleIntent; }
    public void setScheduleIntent(String scheduleIntent) { this.scheduleIntent = scheduleIntent; }
    public String getPreferredTimeWindow() { return preferredTimeWindow; }
    public void setPreferredTimeWindow(String preferredTimeWindow) { this.preferredTimeWindow = preferredTimeWindow; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}
