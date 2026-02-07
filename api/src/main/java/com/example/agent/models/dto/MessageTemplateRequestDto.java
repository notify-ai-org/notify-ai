package com.example.agent.models.dto;

/**
 * DTO for MessageTemplateAgent input.
 */
public class MessageTemplateRequestDto {

    private String eventType;
    private String description;
    private Object payload;
    private String occuredAt;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    public String getOccuredAt() { return occuredAt; }
    public void setOccuredAt(String occuredAt) { this.occuredAt = occuredAt; }
}

