package com.example.agent.models.dto;

/**
 * DTO matching the input schema of RuleProcessorAgent.
 */
public class RuleDefinitionRequestDto {

    private String eventName;
    private String ruleDescription;
    private String ruleName;
    private Object payload;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getRuleDescription() { return ruleDescription; }
    public void setRuleDescription(String ruleDescription) { this.ruleDescription = ruleDescription; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}

