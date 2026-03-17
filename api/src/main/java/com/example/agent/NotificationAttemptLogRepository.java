package com.example.agent;

import com.example.agent.models.NotificationAttemptLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationAttemptLogRepository
        extends JpaRepository<NotificationAttemptLog, Long> {
    List<NotificationAttemptLog> findByProcessedFalseOrderByTimestampAsc(Pageable pageable);
}

