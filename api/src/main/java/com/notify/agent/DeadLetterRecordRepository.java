package com.notify.agent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.notify.agent.models.DeadLetterRecord;
import com.notify.agent.models.DeadLetterRecord.ReplayStatus;

import java.time.Instant;

public interface DeadLetterRecordRepository extends JpaRepository<DeadLetterRecord, Long> {

    Page<DeadLetterRecord> findByReplayStatus(ReplayStatus status, Pageable pageable);

    Page<DeadLetterRecord> findByNotificationId(String notificationId, Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
}

