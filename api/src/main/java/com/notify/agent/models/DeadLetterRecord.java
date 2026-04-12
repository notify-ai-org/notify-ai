package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_record", indexes = {
        @Index(name = "idx_dlq_notification_id", columnList = "notificationId"),
        @Index(name = "idx_dlq_status_created", columnList = "replayStatus, createdAt"),
        @Index(name = "idx_dlq_category_reason", columnList = "failureCategory, failureReasonCode")
})
@Getter
@Setter
public class DeadLetterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Core identifiers
    @Column(nullable = false, length = 128)
    private String notificationId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = true, length = 256)
    private String target;

    // Full original job snapshot (JSON)
    @Lob
    @Column(nullable = false)
    private String originalJobPayload;

    // Optional snapshot of rendered content or vocabulary, if you want
    @Lob
    private String resolvedVocabularyPayload; // JSON
    @Lob
    private String renderedContent; // optional

    // Failure metadata
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FailureCategory failureCategory;

    @Column(nullable = false, length = 64)
    private String failureReasonCode;

    @Lob
    private String failureMessage;

    @Column(length = 256)
    private String exceptionClass;

    @Lob
    private String stackTrace;

    // Attempts
    @Column(nullable = false)
    private int attemptCount;

    private Instant firstAttemptAt;
    private Instant lastAttemptAt;

    // Where it happened
    @Column(length = 128)
    private String workerId;

    @Column(length = 128)
    private String dispatcherInstanceId;

    // Replay status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReplayStatus replayStatus = ReplayStatus.PENDING;

    private Instant replayedAt;
    @Column(length = 128)
    private String replayedBy;

    private Instant discardedAt;
    @Column(length = 128)
    private String discardedBy;
    @Lob
    private String discardReason;

    // Audit
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Value
    @Builder
    public static class FailureInfo {
        FailureCategory category;
        String reasonCode; // e.g. SMTP_TIMEOUT, TEMPLATE_PARSE_ERROR
        String message; // human readable
        String exceptionClass; // ex.getClass().getName()
        String stackTrace; // truncated stack trace
    }

    public enum FailureCategory {
        TRANSIENT,
        PERMANENT,
        UNKNOWN
    }

    public enum ReplayStatus {
        PENDING,
        REPLAYED,
        DISCARDED
    }

}
