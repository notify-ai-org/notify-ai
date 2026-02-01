package com.example.agent.models;

import com.example.agent.enums.AgentStage;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "agent_logs")
@Data
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
}
