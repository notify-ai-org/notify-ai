package com.notify.agent;

import com.notify.agent.models.AgentLog;
import com.notify.agent.models.RawLog.ProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AgentLogRepository extends JpaRepository<AgentLog, String> {
    List<AgentLog> findByAgentIdOrderByTimestampDesc(String agentId);

    List<AgentLog> findByProcessingStatusOrderByTimestampAsc(ProcessingStatus status, Pageable pageable);

    /** Reset any PROCESSING logs back to PENDING (used on startup after crash/restart). */
    @Modifying
    @Query("UPDATE AgentLog l SET l.processingStatus = 'PENDING' WHERE l.processingStatus = 'PROCESSING'")
    int resetStuckProcessingLogs();
}

