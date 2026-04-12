package com.notify.agent.models;

import com.notify.agent.enums.AgentStage;

import java.time.Instant;
import java.util.Map;

/**
 * Event emitted when an agent transitions between stages
 */
public class AgentStageChangeEvent {
    private String agentId;
    private String agentName;
    private AgentStage previousStage;
    private AgentStage currentStage;
    private Instant timestamp;
    private String reason;
    private Map<String, Object> metadata;
    private String correlationId;

    public AgentStageChangeEvent() {}

    public AgentStageChangeEvent(String agentId, String agentName, AgentStage previousStage, 
                                AgentStage currentStage, String reason, Map<String, Object> metadata) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.previousStage = previousStage;
        this.currentStage = currentStage;
        this.timestamp = Instant.now();
        this.reason = reason;
        this.metadata = metadata;
    }

    // Getters and setters
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    
    public AgentStage getPreviousStage() { return previousStage; }
    public void setPreviousStage(AgentStage previousStage) { this.previousStage = previousStage; }
    
    public AgentStage getCurrentStage() { return currentStage; }
    public void setCurrentStage(AgentStage currentStage) { this.currentStage = currentStage; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    @Override
    public String toString() {
        return String.format("AgentStageChangeEvent{agentId='%s', agentName='%s', %s -> %s, reason='%s', timestamp=%s}", 
                           agentId, agentName, previousStage, currentStage, reason, timestamp);
    }
}
