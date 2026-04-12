package com.notify.agent.models.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for EventProcessorAgent output per processed event.
 */
public class EventProcessingResultDto {

    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String result;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String eventName;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String eventDescription;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String eventType;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String occurredAt;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private String priority;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private Map<String, Object> payload;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private List<ChannelDto> channels;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private List<String> ruleExpressions;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private Reasoning reasoning;
    @com.fasterxml.jackson.annotation.JsonProperty(required = true)
    private SafetyChecks safetyChecks;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public void setEventDescription(String eventDescription) {
        this.eventDescription = eventDescription;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public List<ChannelDto> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelDto> channels) {
        this.channels = channels;
    }

    public List<String> getRuleExpressions() {
        return ruleExpressions;
    }

    public void setRuleExpressions(List<String> ruleExpressions) {
        this.ruleExpressions = ruleExpressions;
    }

    public Reasoning getReasoning() {
        return reasoning;
    }

    public void setReasoning(Reasoning reasoning) {
        this.reasoning = reasoning;
    }

    public SafetyChecks getSafetyChecks() {
        return safetyChecks;
    }

    public void setSafetyChecks(SafetyChecks safetyChecks) {
        this.safetyChecks = safetyChecks;
    }

    public static class Reasoning {
        @com.fasterxml.jackson.annotation.JsonProperty(required = false)
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("Agent internal thought process (Maximum 100 words)")
        private String thoughtProcess;
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private List<String> bulletReasons;
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private List<String> memoryUsed;
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private List<String> factsUsed;

        public String getThoughtProcess() {
            return thoughtProcess;
        }

        public void setThoughtProcess(String thoughtProcess) {
            this.thoughtProcess = thoughtProcess;
        }

        public List<String> getBulletReasons() {
            return bulletReasons;
        }

        public void setBulletReasons(List<String> bulletReasons) {
            this.bulletReasons = bulletReasons;
        }

        public List<String> getMemoryUsed() {
            return memoryUsed;
        }

        public void setMemoryUsed(List<String> memoryUsed) {
            this.memoryUsed = memoryUsed;
        }

        public List<String> getFactsUsed() {
            return factsUsed;
        }

        public void setFactsUsed(List<String> factsUsed) {
            this.factsUsed = factsUsed;
        }
    }

    public static class ChannelDto {
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private String channel;

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }

    public static class SafetyChecks {
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private Boolean optOutRespected;
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private Boolean dndRespected;
        @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        private Boolean quotaRespected;

        public Boolean getOptOutRespected() {
            return optOutRespected;
        }

        public void setOptOutRespected(Boolean optOutRespected) {
            this.optOutRespected = optOutRespected;
        }

        public Boolean getDndRespected() {
            return dndRespected;
        }

        public void setDndRespected(Boolean dndRespected) {
            this.dndRespected = dndRespected;
        }

        public Boolean getQuotaRespected() {
            return quotaRespected;
        }

        public void setQuotaRespected(Boolean quotaRespected) {
            this.quotaRespected = quotaRespected;
        }
    }
}
