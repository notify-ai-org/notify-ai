package com.example.agent;

import com.example.agent.models.AgentLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AgentLogRepository extends JpaRepository<AgentLog, String> {
    List<AgentLog> findByAgentIdOrderByTimestampDesc(String agentId);

    List<AgentLog> findByProcessedFalseOrderByTimestampAsc(Pageable pageable);
}

