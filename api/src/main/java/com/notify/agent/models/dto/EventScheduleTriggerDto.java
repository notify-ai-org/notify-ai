package com.notify.agent.models.dto;

/**
 * DTO for EventSchedulerAgent output trigger objects.
 */
public class EventScheduleTriggerDto {

    private String triggerType;
    private String triggerValue;

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getTriggerValue() { return triggerValue; }
    public void setTriggerValue(String triggerValue) { this.triggerValue = triggerValue; }
}

