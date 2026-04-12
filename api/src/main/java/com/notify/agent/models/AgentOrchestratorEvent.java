package com.notify.agent.models;

import com.notify.agent.enums.AgentOrchestratorEventType;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an event emitted by the AgentOrchestrator.
 */
public class AgentOrchestratorEvent {
    private final AgentOrchestratorEventType type;
    private final Map<String, Object> data;
    private final Instant timestamp;

    public AgentOrchestratorEvent(AgentOrchestratorEventType type, Map<String, Object> data, Instant timestamp) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
    }

    public AgentOrchestratorEventType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("AgentOrchestratorEvent{type=%s, data=%s, timestamp=%s}",
                type, data, timestamp);
    }
}
