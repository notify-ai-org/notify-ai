package com.example.agent.models.dto;

/**
 * DTO matching EventSchedulerAgent input schema.
 */
public class EventScheduleRequestDto {

    private String eventName;
    private String eventDescription;
    private String triggerType;
    private String scheduleIntent;
    private String preferredTimeWindow;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getScheduleIntent() { return scheduleIntent; }
    public void setScheduleIntent(String scheduleIntent) { this.scheduleIntent = scheduleIntent; }
    public String getPreferredTimeWindow() { return preferredTimeWindow; }
    public void setPreferredTimeWindow(String preferredTimeWindow) { this.preferredTimeWindow = preferredTimeWindow; }
}

