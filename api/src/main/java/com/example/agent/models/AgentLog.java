package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_logs")
public class AgentLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String agentId;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private LogType logType;

    @Enumerated(EnumType.STRING)
    private AgentStage previousStage;

    @Enumerated(EnumType.STRING)
    private AgentStage newStage;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(columnDefinition = "TEXT")
    private String eventContentJson;

    public enum LogType {
        STAGE_CHANGE,
        EVENT_EMITTED
    }

    public AgentLog() {
    }

    public AgentLog(String agentId, Instant timestamp, LogType logType, AgentStage previousStage, AgentStage newStage,
            String reason, String metadataJson, String eventContentJson) {
        this.agentId = agentId;
        this.timestamp = timestamp;
        this.logType = logType;
        this.previousStage = previousStage;
        this.newStage = newStage;
        this.reason = reason;
        this.metadataJson = metadataJson;
        this.eventContentJson = eventContentJson;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public LogType getLogType() {
        return logType;
    }

    public void setLogType(LogType logType) {
        this.logType = logType;
    }

    public AgentStage getPreviousStage() {
        return previousStage;
    }

    public void setPreviousStage(AgentStage previousStage) {
        this.previousStage = previousStage;
    }

    public AgentStage getNewStage() {
        return newStage;
    }

    public void setNewStage(AgentStage newStage) {
        this.newStage = newStage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getEventContentJson() {
        return eventContentJson;
    }

    public void setEventContentJson(String eventContentJson) {
        this.eventContentJson = eventContentJson;
    }
}
