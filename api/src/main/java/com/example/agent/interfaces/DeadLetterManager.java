package com.example.agent.interfaces;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.agent.models.DeadLetterRecord;
import com.example.agent.models.NotificationJob;

import java.time.Instant;
import java.util.Map;

public interface DeadLetterManager {

    Page<DeadLetterRecord> listPending(Pageable pageable);

    Page<DeadLetterRecord> searchByNotificationId(String notificationId, Pageable pageable);

    DeadLetterRecord get(long id);

    void replay(long id, String actor);

    void discard(long id, String actor, String reason);

    long purgeOlderThan(Instant cutoff);

    void enqueue(NotificationJob job, Throwable failure, int attemptCount, Instant firstAttemptAt,
            Instant lastAttemptAt, String workerId, String dispatcherInstanceId, Map<String, Object> resolvedVocabulary,
            String renderedContent);
}

