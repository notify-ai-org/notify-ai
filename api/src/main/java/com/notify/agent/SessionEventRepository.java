package com.notify.agent;

import com.notify.agent.models.SessionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SessionEventRepository extends JpaRepository<SessionEventEntity, String> {

    /**
     * Fetch session events within a time window, oldest first.
     * Used by EventHistoryPlanner to retrieve recent context.
     */
    List<SessionEventEntity> findBySession_SessionIdAndOccurredAtAfterOrderByOccurredAtAsc(
            String sessionId, Instant since);
}
