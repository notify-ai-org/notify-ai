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
    private Reasoning reasoning;
    private SafetyChecks safetyChecks;

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

    public List<Map<String, String>> getChannels() {
        return channels;
    }

    public void setChannels(List<Map<String, String>> channels) {
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
        private List<String> bulletReasons;
        private List<String> memoryUsed;
        private List<String> factsUsed;

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

    public static class SafetyChecks {
        private Boolean optOutRespected;
        private Boolean dndRespected;
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
