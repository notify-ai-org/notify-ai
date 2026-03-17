package com.example.agent.models;

import com.example.agent.enums.AgentStage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "agent_logs")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLog extends RawLog {

    private String agentId;

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

