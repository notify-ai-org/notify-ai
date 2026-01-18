package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_snapshots")
public class AgentSnapshot {
    @Id
    private String agentId;

    private String agentName;

    @Enumerated(EnumType.STRING)
    private AgentStage currentStage;

    private Instant createdAt;

    private Instant lastActivityAt;

    private String currentTaskId;

    @Column(columnDefinition = "TEXT")
    private String agentStateJson;

    public AgentSnapshot() {
    }

    public AgentSnapshot(String agentId, String agentName, AgentStage currentStage, Instant createdAt,
            Instant lastActivityAt, String currentTaskId, String agentStateJson) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.currentStage = currentStage;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.currentTaskId = currentTaskId;
        this.agentStateJson = agentStateJson;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public AgentStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(AgentStage currentStage) {
        this.currentStage = currentStage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getAgentStateJson() {
        return agentStateJson;
    }

    public void setAgentStateJson(String agentStateJson) {
        this.agentStateJson = agentStateJson;
    }
}
