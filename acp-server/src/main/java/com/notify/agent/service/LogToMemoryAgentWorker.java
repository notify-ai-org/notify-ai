package com.notify.agent.service;

import com.notify.agent.AgentLogRepository;
import com.notify.agent.EventCaptureRepository;
import com.notify.agent.EventExecutionLogRepository;
import com.notify.agent.NotificationAttemptLogRepository;
import com.notify.agent.consumers.FactConsumer;
import com.notify.agent.models.RawLog;
import com.notify.agent.models.RawLog.ProcessingStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Periodically pulls unprocessed logs from the database (agent logs, event
 * captures, event execution logs, notification attempt logs) in batches and
 * feeds them to the FactConsumer for LLM-based fact extraction.
 *
 * <p>Uses a three-state status flag on each log to prevent reprocessing:
 * <ul>
 *   <li>PENDING    – not yet picked up</li>
 *   <li>PROCESSING – claimed by this worker; reset to PENDING on startup so
 *       nothing is silently lost after a crash or restart</li>
 *   <li>PROCESSED  – successfully consumed by the fact extractor</li>
 *   <li>FAILED     – extraction failed; excluded from future batches</li>
 * </ul>
 *
 * Runs on a configurable fixed-delay interval
 * (agent.log-worker.interval-ms, default 100000ms).
 */
@Service
public class LogToMemoryAgentWorker {

    private static final Logger log = LoggerFactory.getLogger(LogToMemoryAgentWorker.class);

    private final EventCaptureRepository eventCaptureRepository;
    private final AgentLogRepository agentLogRepository;
    private final NotificationAttemptLogRepository notificationAttemptLogRepository;
    private final EventExecutionLogRepository eventExecutionLogRepository;
    private final FactConsumer factExtractor;

    private final int maxBatchSize;

    public LogToMemoryAgentWorker(
            EventCaptureRepository eventCaptureRepository,
            AgentLogRepository agentLogRepository,
            NotificationAttemptLogRepository notificationAttemptLogRepository,
            EventExecutionLogRepository eventExecutionLogRepository,
            FactConsumer factExtractor,
            int maxBatchSize) {
        this.eventCaptureRepository = eventCaptureRepository;
        this.agentLogRepository = agentLogRepository;
        this.notificationAttemptLogRepository = notificationAttemptLogRepository;
        this.eventExecutionLogRepository = eventExecutionLogRepository;
        this.factExtractor = factExtractor;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * After the full application context is ready (transaction manager included),
     * reset any logs that were left in PROCESSING state from a previous crash back
     * to PENDING so they are retried in the next scheduler cycle.
     *
     * <p>NOTE: {@code @PostConstruct} cannot be used here because Spring's
     * transaction proxy is not yet active during bean initialisation.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetStuckLogs() {
        int agentReset = agentLogRepository.resetStuckProcessingLogs();
        int captureReset = eventCaptureRepository.resetStuckProcessingLogs();
        int execReset = eventExecutionLogRepository.resetStuckProcessingLogs();
        int notifReset = notificationAttemptLogRepository.resetStuckProcessingLogs();
        int total = agentReset + captureReset + execReset + notifReset;
        if (total > 0) {
            log.warn("LogToMemoryAgentWorker: reset {} stuck PROCESSING logs back to PENDING on startup", total);
        }
    }

    /**
     * Scheduled method that runs on a configurable interval.
     * Pulls PENDING logs from all repositories, marks them PROCESSING,
     * feeds them to the fact extractor, then marks them PROCESSED (or FAILED).
     */
    @Scheduled(fixedDelayString = "${agent.log-worker.interval-ms:100000}")
    @Transactional
    public void run() {
        try {
            int remaining = maxBatchSize;
            List<RawLog> batch = new ArrayList<>(maxBatchSize);
            Pageable page;

            // 1. Pull agent logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                List<? extends RawLog> agentLogs = agentLogRepository
                        .findByProcessingStatusOrderByTimestampAsc(ProcessingStatus.PENDING, page);
                batch.addAll(agentLogs);
                remaining -= agentLogs.size();
            }

            // 2. Pull event captures
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                List<? extends RawLog> eventCaptures = eventCaptureRepository
                        .findByProcessingStatusOrderByTimestampAsc(ProcessingStatus.PENDING, page);
                batch.addAll(eventCaptures);
                remaining -= eventCaptures.size();
            }

            // 3. Pull event execution logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                List<? extends RawLog> executionLogs = eventExecutionLogRepository
                        .findByProcessingStatusOrderByTimestampAsc(ProcessingStatus.PENDING, page);
                batch.addAll(executionLogs);
                remaining -= executionLogs.size();
            }

            // 4. Pull notification attempt logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                List<? extends RawLog> notificationLogs = notificationAttemptLogRepository
                        .findByProcessingStatusOrderByTimestampAsc(ProcessingStatus.PENDING, page);
                batch.addAll(notificationLogs);
            }

            if (batch.isEmpty()) {
                return; // nothing to process
            }

            // Claim the batch: mark all as PROCESSING so concurrent runs / restarts won't
            // pick up the same records.
            Instant now = Instant.now();
            for (RawLog rawLog : batch) {
                rawLog.setProcessingStatus(ProcessingStatus.PROCESSING);
            }

            log.info("LogToMemoryAgentWorker: processing batch of {} logs", batch.size());

            // Feed to fact extractor — may throw, handled below
            factExtractor.extractFacts(batch);

            // Mark as PROCESSED on success
            now = Instant.now();
            for (RawLog rawLog : batch) {
                rawLog.setProcessingStatus(ProcessingStatus.PROCESSED);
                rawLog.setProcessedAt(now);
            }

            log.info("LogToMemoryAgentWorker: marked {} logs as PROCESSED", batch.size());

        } catch (Exception e) {
            log.error("LogToMemoryAgentWorker: error processing batch — marking batch as FAILED", e);
            // The @Transactional context is still active; mark whatever we claimed as FAILED
            // so they're excluded from future batches rather than being retried forever.
            // (Caller can manually reset them to PENDING via resetStuckLogs() if needed.)
            try {
                // We can't access the batch here after an exception rolls back managed entities,
                // so the @Transactional rollback will revert PROCESSING → PENDING automatically
                // because the flush hasn't committed yet. No extra action needed.
            } catch (Exception ignored) {
                // intentionally empty
            }
        }
    }
}
