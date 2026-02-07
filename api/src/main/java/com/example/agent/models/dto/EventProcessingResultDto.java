package com.example.agent.models.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for EventProcessorAgent output per processed event.
 */
public class EventProcessingResultDto {

    private String eventName;
    private String eventDescription;
    private String eventType;
    private String occurredAt;
    private Map<String, Object> payload;
    private List<Map<String, String>> channels;
    private List<String> ruleExpressions;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public List<Map<String, String>> getChannels() { return channels; }
    public void setChannels(List<Map<String, String>> channels) { this.channels = channels; }
    public List<String> getRuleExpressions() { return ruleExpressions; }
    public void setRuleExpressions(List<String> ruleExpressions) { this.ruleExpressions = ruleExpressions; }
}

