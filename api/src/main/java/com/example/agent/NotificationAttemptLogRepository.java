package com.example.agent;

import com.example.agent.models.NotificationAttemptLog;
import com.example.agent.models.RawLog.ProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationAttemptLogRepository
        extends JpaRepository<NotificationAttemptLog, Long> {
    List<NotificationAttemptLog> findByProcessingStatusOrderByTimestampAsc(ProcessingStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE NotificationAttemptLog l SET l.processingStatus = 'PENDING' WHERE l.processingStatus = 'PROCESSING'")
    int resetStuckProcessingLogs();
}
