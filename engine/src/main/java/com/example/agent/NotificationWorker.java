package com.example.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.agent.DispatcherWorkerPool.DispatcherProperties;
import com.example.agent.models.ConnectorMetrics;
import com.example.agent.models.NotificationAttemptLog;
import com.example.agent.models.NotificationConnector;
import com.example.agent.models.NotificationJob;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NotificationWorker implements Runnable {

    public final String workerId = UUID.randomUUID().toString();

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final BlockingQueue<NotificationJob> queue;
    private final ConnectorRegistry connectorRegistry;
    private final NotificationAttemptLogRepository logRepo;
    private final DispatcherProperties properties;
    private Instant lastActiveAt;
    private final RestTemplate rest;

    private volatile boolean running = true;
    private volatile WorkerStatus status = WorkerStatus.INITIALIZING;

    private final AtomicReference<ConnectorMetrics> metrics =
            new AtomicReference<>(new ConnectorMetrics());

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$(\\w+)");

    private AtomicReference<List<NotificationAttemptLog>> logBuffer = null;

    NotificationJob currentJob;

    /**
     * @return the logBuffer
     */
    public AtomicReference<List<NotificationAttemptLog>> getLogBuffer() {
        return logBuffer;
    }

    public void setLogBuffer(AtomicReference<List<NotificationAttemptLog>> buffer){
        this.logBuffer = buffer;
    }

    /**
     * @return the currentJob
     */
    public NotificationJob getCurrentJob() {
        return currentJob;
    }

    /**
     * @param currentJob the currentJob to set
     */
    public void setCurrentJob(NotificationJob currentJob) {
        this.currentJob = currentJob;
    }

    /**
     * @return the lastActiveAt
     */
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * @return the workerId
     */
    public String getWorkerId() {
        return workerId;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public ConnectorMetrics getMetrics() {
        return metrics.get();
    }


    @Override
    public void run() {
        log.info("Worker started: {}", Thread.currentThread().getName());
        status = WorkerStatus.AVAILABLE;
        Thread.currentThread().setName(workerId);

        while (running) {
            try {
                NotificationJob job = queue.poll(2, TimeUnit.SECONDS);
                if (job == null) {
                    continue;
                }

                setCurrentJob(job);
                status = WorkerStatus.UNAVAILABLE;
                process(job);
                setCurrentJob(null);
                lastActiveAt = Instant.now();
                status = WorkerStatus.AVAILABLE;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                status = WorkerStatus.AVAILABLE;
                setCurrentJob(null);
                break;
            } catch (Throwable t) {
                // absolutely never let the worker die
                log.error("Fatal error in worker loop", t);
                status = WorkerStatus.AVAILABLE;
                setCurrentJob(null);
            }
        }

        status = WorkerStatus.SHUTDOWN;
        log.info("Worker shutdown: {}", Thread.currentThread().getName());
    }

    public boolean assignJob(NotificationJob job) {
        if (!running) return false;

        try {
            return queue.offer(job, 1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean isAvailable() {
        return running && status == WorkerStatus.AVAILABLE;
    }

    public void shutdown() {
        running = false;
    }

    /* =========================
     * Job processing
     * ========================= */

    private void process(NotificationJob job) {
        Instant start = Instant.now();
        log.debug("Processing job {}", job.getId());

        try {
            Map<String, String> vocabulary = fetchVocabulary(job.getCallbackUrl());

            if (!executeRules(job)) {
                log.info("Rules evaluated to false, skipping notification {}",
                        job.getId());
                return;
            }

            String renderedContent = render(job.getTemplate(), vocabulary);

            job.setTemplate(renderedContent);

            NotificationConnector connector = connectorRegistry.get(job.getChannel());

            connector.bind(null);

            connector.init(metrics);

            connector.send(job);
            
            recordSuccess(job, start);

        } catch (Exception ex) {
            recordFailure(job, start, ex);
            throw ex; // important: let dispatcher retry/DLQ decide
        }
    }

    /* =========================
     * Rendering
     * ========================= */

    public String render(String template, Map<String, String> vocab) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vocab.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /* =========================
     * External calls
     * ========================= */

    private Map<String, String> fetchVocabulary(String callbackUrl) {
        try {
            ResponseEntity<Map<String, String>> res =
                    rest.getForEntity(callbackUrl,
                            (Class<Map<String, String>>) (Class<?>) Map.class);

            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new IllegalStateException("Vocabulary fetch failed");
            }
            return res.getBody();

        } catch (RestClientException ex) {
            throw new RuntimeException("Failed to fetch vocabulary", ex);
        }
    }

    private boolean executeRules(NotificationJob job) {
        if (job.getRuleExpressions() == null || job.getRuleExpressions().isBlank()) {
            return true;
        }

        try {
            ResponseEntity<Boolean> res =
                    rest.postForEntity(
                            job.getCallbackUrl(),
                            job.getRuleExpressions(),
                            Boolean.class
                    );

            return Boolean.TRUE.equals(res.getBody());

        } catch (RestClientException ex) {
            throw new RuntimeException("Rule execution failed", ex);
        }
    }

    /* =========================
     * Metrics & logging
     * ========================= */

    private void recordSuccess(NotificationJob job, Instant start) {
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

        //metrics.updateAndGet(m -> m.recordSuccess(durationMs));

        log.info("Notification {} sent via {} in {} ms",
                job.getId(),
                job.getChannel(),
                durationMs);
    }

    private void recordFailure(NotificationJob job, Instant start, Exception ex) {
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        //metrics.updateAndGet(m -> m.recordFailure(durationMs));

        log.warn("Notification {} failed after {} ms",
                job.getId(),
                durationMs,
                ex);

        saveFailure(job, ex);
    }

    private void saveFailure(NotificationJob job, Exception ex) {
        NotificationAttemptLog logEntry = new NotificationAttemptLog();
        logEntry.setTimestamp(Instant.now());
        logEntry.setChannel(job.getChannel());
        logEntry.setError(ex.getMessage());
        logBuffer.get().add(logEntry);
    }

    /* =========================
     * Worker states
     * ========================= */

    public enum WorkerStatus {
        INITIALIZING,
        AVAILABLE,
        UNAVAILABLE,
        SHUTDOWN
    }
}
