package com.example.agent;

import com.example.agent.enums.AgentStage;
import com.example.agent.models.AgentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AgentSnapshot}.
 * Provides read/write access to agent state snapshots stored in the DB.
 */
@Repository
public interface AgentSnapshotRepository extends JpaRepository<AgentSnapshot, String> {

    /**
     * Find all snapshots whose last recorded stage is among the given stages.
     * Useful for rehydrating agents that were RUNNING or PAUSED at shutdown.
     */
    List<AgentSnapshot> findByCurrentStageIn(List<AgentStage> stages);

    /**
     * Find all snapshots that had an in-progress task id at the time of the last
     * snapshot (i.e. the agent had an active task when it was persisted).
     */
    List<AgentSnapshot> findByCurrentTaskIdIsNotNull();
}
