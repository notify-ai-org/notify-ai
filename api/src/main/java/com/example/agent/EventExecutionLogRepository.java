package com.example.agent;

import com.example.agent.models.EventExecutionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventExecutionLogRepository extends JpaRepository<EventExecutionLog, String> {
    List<EventExecutionLog> findByProcessedFalseOrderByTimestampAsc(Pageable pageable);
}
