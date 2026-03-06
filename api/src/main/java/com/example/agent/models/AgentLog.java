package com.example.agent.models;

import com.example.agent.enums.AgentStage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "agent_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String agentId;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private LogType type;

    @Enumerated(EnumType.STRING)
    private AgentStage previousStage;

    @Enumerated(EnumType.STRING)
    private AgentStage currentStage;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(columnDefinition = "TEXT")
    private String eventContent;

    public enum LogType {
        STAGE_CHANGE,
        EVENT_EMITTED,
        TASK_STARTED,
        TASK_FAILED
    }
}
