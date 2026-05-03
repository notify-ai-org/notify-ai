package com.notify.agent.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.annotations.ManagedConfiguration.ConfigSource;

/**
 * Manages request idempotency via Redis.
 *
 * <p>Each request with an idempotency key progresses through two states:
 * <ol>
 *   <li>{@code PROCESSING} – set atomically when the key is first seen.
 *   <li>{@code COMPLETED} – set once the upstream filter chain succeeds.
 * </ol>
 *
 * <p>Callers:
 * <ul>
 *   <li>Call {@link #acquireLock(String, String, String)} before processing.
 *   <li>Call {@link #markCompleted(String)} after successful processing.
 *   <li>Call {@link #releaseLock(String)} on failure so the client can retry.
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED   = "COMPLETED";
    private static final String KEY_PREFIX         = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Value("${acp.idempotency.retry-count:5}")
    @ManagedConfiguration(key = "acp.idempotency.retry-count", source = ConfigSource.CONFIG_MAP)
    private int retryCount;

    @Value("${acp.idempotency.retry-interval-ms:500}")
    @ManagedConfiguration(key = "acp.idempotency.retry-interval-ms", source = ConfigSource.CONFIG_MAP)
    private long retryIntervalMs;

    @Value("${acp.idempotency.expiry-seconds:86400}")
    @ManagedConfiguration(key = "acp.idempotency.expiry-seconds", source = ConfigSource.CONFIG_MAP)
    private long expirySeconds;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns the canonical Redis key for a given client + idempotency header value.
     */
    public String redisKey(String clientId, String idempotencyKey) {
        return KEY_PREFIX + clientId + ":" + idempotencyKey;
    }

    /**
     * Attempts to acquire the idempotency lock for this request.
     *
     * @param clientId       client performing the request
     * @param idempotencyKey raw value from the request header
     * @param redisKeyOut    single-element array used to return the computed Redis key
     * @return {@link AcquireResult} indicating outcome
     */
    public AcquireResult acquireLock(String clientId, String idempotencyKey, String[] redisKeyOut) {
        String key = redisKey(clientId, idempotencyKey);
        redisKeyOut[0] = key;

        for (int i = 0; i < retryCount; i++) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(key, STATUS_PROCESSING, Duration.ofSeconds(expirySeconds));
            if (Boolean.TRUE.equals(isNew)) {
                log.debug("Idempotency lock acquired: {}", key);
                return AcquireResult.ACQUIRED;
            }

            String status = redisTemplate.opsForValue().get(key);
            if (STATUS_COMPLETED.equals(status)) {
                log.info("Idempotency key already completed: {}", key);
                return AcquireResult.ALREADY_COMPLETED;
            }

            // Still PROCESSING — wait and retry
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return AcquireResult.INTERRUPTED;
            }
        }

        // Exhausted retries
        String status = redisTemplate.opsForValue().get(key);
        if (STATUS_COMPLETED.equals(status)) {
            return AcquireResult.ALREADY_COMPLETED;
        }
        log.warn("Idempotency lock not acquired after {} retries: {}", retryCount, key);
        return AcquireResult.STILL_PROCESSING;
    }

    /**
     * Marks an idempotency key as COMPLETED after successful processing.
     */
    public void markCompleted(String redisKey) {
        if (redisKey == null) return;
        redisTemplate.opsForValue().set(redisKey, STATUS_COMPLETED, Duration.ofSeconds(expirySeconds));
        log.debug("Idempotency key marked COMPLETED: {}", redisKey);
    }

    /**
     * Deletes the idempotency key so the client can safely retry on failure.
     */
    public void releaseLock(String redisKey) {
        if (redisKey == null) return;
        redisTemplate.delete(redisKey);
        log.debug("Idempotency key released (failure path): {}", redisKey);
    }

    /**
     * Result of an {@link #acquireLock} attempt.
     */
    public enum AcquireResult {
        /** Lock taken — proceed with request processing. */
        ACQUIRED,
        /** A previous identical request already succeeded — reject as duplicate. */
        ALREADY_COMPLETED,
        /** A concurrent identical request is still in-flight — reject. */
        STILL_PROCESSING,
        /** Thread was interrupted while waiting — treat as a server error. */
        INTERRUPTED
    }
}
