package com.notify.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.notify.agent.models.ConnectorMetrics;
import com.notify.agent.models.NotificationAttemptLog;
import com.notify.agent.interfaces.NotificationConnector;
import com.notify.agent.models.NotificationJob;
import com.notify.agent.models.WorkerSnapshot;
import com.notify.agent.models.subject.Subject;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NotificationWorker implements Runnable {

    public final String workerId = UUID.randomUUID().toString();

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private BlockingQueue<NotificationJob> queue;
    private final ConnectorRegistry connectorRegistry;
    private Instant lastActiveAt;

    private volatile boolean running = true;
    private volatile WorkerStatus status = WorkerStatus.INITIALIZING;

    private final AtomicReference<ConnectorMetrics> metrics = new AtomicReference<>(new ConnectorMetrics());

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$(\\w+)");

    private AtomicReference<List<NotificationAttemptLog>> logBuffer = null;

    private NotificationJob currentJob = null;

    private final NotificationJobRepository jobRepository;

    /**
     * @return the logBuffer
     */
    public AtomicReference<List<NotificationAttemptLog>> getLogBuffer() {
        return logBuffer;
    }

    public void setLogBuffer(AtomicReference<List<NotificationAttemptLog>> buffer) {
        this.logBuffer = buffer;
    }

    public void loadState(WorkerSnapshot snapshot) {
        this.lastActiveAt = snapshot.getLastActiveAt();
        this.status = WorkerStatus.valueOf(snapshot.getStatus());
        this.currentJob = jobRepository.findById(snapshot.getJobId()).orElse(null);
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
        this.queue = new ArrayBlockingQueue<>(100);
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
        if (!running)
            return false;

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

    /*
     * =========================
     * Job processing
     * =========================
     */

    private void process(NotificationJob job) {
        Instant start = Instant.now();
        log.debug("Processing job {}", job.getId());

        try {
            Map<String, String> vocabulary = job.getAttributes();
            String renderedContent = render(job.getTemplate(), vocabulary);
            job.setTemplate(renderedContent);
            NotificationConnector connector = connectorRegistry.get(job.getChannel());
            connector.bind(null);
            connector.init(metrics);

            for (Subject subject : job.getSubjects()) {
                try {
                    connector.send(job, subject);
                    recordSuccess(job, start, subject);
                } catch (Exception e) {
                    recordFailure(job, start, subject, e);
                }
            }

            connector.close();
        } catch (Exception ex) {
            recordFailure(job, start, null, ex);
            throw ex; // important: let dispatcher retry/DLQ decide
        }
    }

    /*
     * =========================
     * Rendering
     * =========================
     */

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

    /*
     * =========================
     * Metrics & logging
     * =========================
     */

    private void recordSuccess(NotificationJob job, Instant start, Subject subject) {
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        log.info("Notification {} sent via {} to {} in {} ms",
                job.getId(),
                job.getChannel(),
                subject.getAddress(),
                durationMs);
        saveAttemptLog(job, subject, null);
    }

    private void recordFailure(NotificationJob job, Instant start, Subject subject, Exception ex) {
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        // metrics.updateAndGet(m -> m.recordFailure(durationMs));

        log.warn("Notification {} failed after {} ms",
                job.getId(),
                durationMs,
                ex);

        saveAttemptLog(job, subject, ex);
    }

    private void saveAttemptLog(NotificationJob job, Subject subject, Exception ex) {
        NotificationAttemptLog logEntry = new NotificationAttemptLog();
        logEntry.setTimestamp(Instant.now());
        logEntry.setChannel(job.getChannel());
        logEntry.setError(ex.getMessage());
        logEntry.setEventType(job.getEventType());
        logEntry.setResult(ex != null ? "FAILED" : "SUCCESS");
        logEntry.setDispatchMode(job.getDispatchMode());
        logEntry.setTemplate(job.getTemplate());
        logEntry.setPriority(job.getPriority());
        logEntry.setLastProcessedBy(workerId);
        logEntry.setTarget(subject == null ? "" : subject.getAddress());
        logBuffer.get().add(logEntry);
    }

    /*
     * =========================
     * Worker states
     * =========================
     */

    public enum WorkerStatus {
        INITIALIZING,
        AVAILABLE,
        UNAVAILABLE,
        SHUTDOWN
    }
}
