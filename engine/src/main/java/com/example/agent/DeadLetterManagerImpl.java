package com.example.agent;

import com.example.agent.interfaces.DeadLetterManager;
import com.example.agent.models.DeadLetterRecord;
import com.example.agent.models.DeadLetterRecord.FailureCategory;
import com.example.agent.models.DeadLetterRecord.FailureInfo;
import com.example.agent.models.DeadLetterRecord.ReplayStatus;
import com.example.agent.models.NotificationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class DeadLetterManagerImpl implements DeadLetterManager {

    private final DeadLetterRecordRepository repo;

    private NotificationDispatcher notificationDispatcher;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Page<DeadLetterRecord> listPending(Pageable pageable) {
        return repo.findByReplayStatus(ReplayStatus.PENDING, pageable);
    }

    @Override
    public Page<DeadLetterRecord> searchByNotificationId(String notificationId, Pageable pageable) {
        return repo.findByNotificationId(notificationId, pageable);
    }

    @Override
    public DeadLetterRecord get(long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + id));
    }

    @Override
    public void enqueue(
            NotificationJob job,
            Throwable failure,
            int attemptCount,
            Instant firstAttemptAt,
            Instant lastAttemptAt,
            String workerId,
            String dispatcherInstanceId,
            Map<String, Object> resolvedVocabulary,
            String renderedContent) {

        FailureInfo failureInfo = classify(failure);

        // Persist FIRST (source of truth)
        enqueue(
                job,
                failureInfo,
                attemptCount,
                firstAttemptAt,
                lastAttemptAt,
                workerId,
                dispatcherInstanceId,
                resolvedVocabulary,
                renderedContent);

    }

    public Long enqueue(NotificationJob job,
            FailureInfo fi,
            int attemptCount,
            Instant firstAttemptAt,
            Instant lastAttemptAt,
            String workerId,
            String dispatcherInstanceId,
            Map<String, Object> resolvedVocabulary,
            String renderedContent) {

        DeadLetterRecord r = new DeadLetterRecord();
        r.setNotificationId(job.getId());
        r.setChannel(job.getChannel());
        r.setAttemptCount(attemptCount);
        r.setFirstAttemptAt(firstAttemptAt);
        r.setLastAttemptAt(lastAttemptAt);
        r.setWorkerId(workerId);
        r.setDispatcherInstanceId(dispatcherInstanceId);

        r.setFailureCategory(fi.getCategory());
        r.setFailureReasonCode(fi.getReasonCode());
        r.setFailureMessage(fi.getMessage());
        r.setExceptionClass(fi.getExceptionClass());
        r.setStackTrace(fi.getStackTrace());

        r.setReplayStatus(ReplayStatus.PENDING);

        try {
            r.setOriginalJobPayload(mapper.writeValueAsString(job));
            if (resolvedVocabulary != null) {
                r.setResolvedVocabularyPayload(mapper.writeValueAsString(resolvedVocabulary));
            }
        } catch (Exception e) {
            // fallback: store minimal info, never lose the record
            r.setOriginalJobPayload("{\"id\":\"" + job.getId() + "\",\"note\":\"json_failed\"}");
        }

        r.setRenderedContent(renderedContent);

        return repo.save(r).getId();
    }

    @Override
    @Transactional
    public void replay(long id, String actor) {
        DeadLetterRecord r = get(id);

        if (r.getReplayStatus() != ReplayStatus.PENDING) {
            throw new IllegalStateException("DLQ record is not pending: " + r.getReplayStatus());
        }

        NotificationJob job = deserializeJob(r.getOriginalJobPayload());

        notificationDispatcher.pushJob(job, Integer.MIN_VALUE, 2000);

        r.setReplayStatus(ReplayStatus.REPLAYED);
        r.setReplayedAt(Instant.now());
        r.setReplayedBy(actor);

        repo.save(r);
    }

    @Override
    @Transactional
    public void discard(long id, String actor, String reason) {
        DeadLetterRecord r = get(id);
        if (r.getReplayStatus() != ReplayStatus.PENDING) {
            throw new IllegalStateException("DLQ record is not pending: " + r.getReplayStatus());
        }
        r.setReplayStatus(ReplayStatus.DISCARDED);
        r.setDiscardedAt(Instant.now());
        r.setDiscardedBy(actor);
        r.setDiscardReason(reason);
        repo.save(r);
    }

    @Override
    @Transactional
    public long purgeOlderThan(Instant cutoff) {
        return repo.deleteByCreatedAtBefore(cutoff);
    }

    private NotificationJob deserializeJob(String json) {
        try {
            return mapper.readValue(json, NotificationJob.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize job payload from DLQ", e);
        }
    }

    public FailureInfo classify(Throwable t) {

        // TRANSIENT: timeouts, 5xx, temporary dependency issues
        if (isTimeout(t)) {
            return FailureInfo.builder()
                    .category(FailureCategory.TRANSIENT)
                    .reasonCode("TIMEOUT")
                    .message(safeMsg(t))
                    .exceptionClass(t.getClass().getName())
                    .stackTrace(stack(t))
                    .build();
        }

        if (t instanceof HttpStatusCodeException) {
            HttpStatusCode code = ((HttpStatusCodeException) t).getStatusCode();
            if (code.is5xxServerError() || code.value() == 429) {
                return FailureInfo.builder()
                        .category(FailureCategory.TRANSIENT)
                        .reasonCode(code.value() == 429 ? "RATE_LIMITED" : "HTTP_5XX")
                        .message(safeMsg(t))
                        .exceptionClass(t.getClass().getName())
                        .stackTrace(stack(t))
                        .build();
            }
            // 4xx usually permanent unless you decide otherwise
            return FailureInfo.builder()
                    .category(FailureCategory.PERMANENT)
                    .reasonCode("HTTP_4XX")
                    .message(safeMsg(t))
                    .exceptionClass(t.getClass().getName())
                    .stackTrace(stack(t))
                    .build();
        }

        // PERMANENT examples (customize for your engine)
        if (t instanceof IllegalArgumentException) {
            return FailureInfo.builder()
                    .category(FailureCategory.PERMANENT)
                    .reasonCode("INVALID_ARGUMENT")
                    .message(safeMsg(t))
                    .exceptionClass(t.getClass().getName())
                    .stackTrace(stack(t))
                    .build();
        }

        // UNKNOWN default (bugs, NPE, etc.)
        return FailureInfo.builder()
                .category(FailureCategory.UNKNOWN)
                .reasonCode("UNEXPECTED")
                .message(safeMsg(t))
                .exceptionClass(t.getClass().getName())
                .stackTrace(stack(t))
                .build();
    }

    private boolean isTimeout(Throwable t) {
        return t instanceof SocketTimeoutException
                || t instanceof TimeoutException
                || (t.getCause() != null && isTimeout(t.getCause()));
    }

    private String safeMsg(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private String stack(Throwable t) {
        // Keep it bounded to avoid giant DB rows
        StringBuilder sb = new StringBuilder();
        int limit = 50;
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append(el).append("\n");
            if (--limit == 0)
                break;
        }
        return sb.toString();
    }
}
