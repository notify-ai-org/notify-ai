package com.example.agent.models;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.agent.models.subject.Subject;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

@Entity
@Data
@Builder
public class NotificationJob {

    /* ================================
     * 1. Identity & Idempotency
     * ================================ */

    /**
     * Globally unique notification identifier.
     * Used for idempotency across retries, replays, and channels.
     */
    @Id
    @GeneratedValue
    private String id;
    /**
     * Optional idempotency key provided by the source system.
     * Multiple notificationIds may share the same idempotencyKey
     * (e.g. daily digest).
     */
    private String idempotencyKey;

    /**
     * Version of this job schema.
     * Enables backward-compatible evolution.
     */
    private int schemaVersion;

    /* ================================
     * 2. Source & Intent
     * ================================ */

    /**
     * Originating system or service.
     * Example: order-service, auth-service, billing-cron
     */
    private String source;

    @Transient
    private List<Subject> subjects;

    /**
     * Event or reason for notification.
     * Example: ORDER_PLACED, PASSWORD_RESET, DAILY_SUMMARY
     */
    private String eventType;

    private String eventName;

    /**
     * Correlation ID for tracing across services.
     */
    private String correlationId;

    /* ================================
     * 3. Dispatch Semantics
     * ================================ */

    /**
     * EVENT  -> dispatched immediately
     * SCHEDULED -> dispatched at scheduledAt
     */
    private DispatchMode dispatchMode;

    public enum DispatchMode {
        EVENT,        // immediate
        RETRY,
        RECONCILE,
        SCHEDULED     // time-based
    }
    

    /**
     * Used only when dispatchMode == SCHEDULED.
     */
    private Instant scheduledAt;

    /**
     * Priority influences worker selection or queue ordering.
     */
    private NotificationPriority priority;

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }


    /* ================================
     * 5. Channel & Payload
     * ================================ */

    /**
     * Channel identifier.
     * Example: email, sms, webhook, push
     */
    private String channel;

    /**
     * Target address.
     * Example: email address, phone number, webhook URL.
     */
    private String target;

    /**
     * Template identifier or raw template.
     */
    private String template;

    /**
     * Callback URL used to fetch vocabulary values.
     */
    private String callbackUrl;

    /**
     * Channel-specific attributes.
     * Example:
     *  - email.subject
     *  - sms.statusCallback
     *  - webhook.secret
     */
    @Transient
    private Map<String, String> attributes;

    /* ================================
     * 6. Observability & Safety
     * ================================ */

    /**
     * Worker or dispatcher instance that last processed this job.
     */
    private String lastProcessedBy;

    /**
     * Optional checksum/hash of payload for tamper detection.
     */
    private String payloadHash;


}
