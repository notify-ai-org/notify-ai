package com.example.agent.service;

import com.example.agent.AgentLogRepository;
import com.example.agent.EventCaptureRepository;
import com.example.agent.EventExecutionLogRepository;
import com.example.agent.NotificationAttemptLogRepository;
import com.example.agent.consumers.FactConsumer;
import com.example.agent.models.RawLog;

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

/**
 * Periodically pulls unprocessed logs from the database (agent logs, event
 * captures, event execution logs, notification attempt logs) in batches and
 * feeds them to the FactConsumer for LLM-based fact extraction.
 *
 * Runs on a configurable fixed-delay interval
 * (agent.log-worker.interval-ms, default 10000ms).
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
     * Scheduled method that runs on a configurable interval.
     * Pulls unprocessed logs from all repositories, merges them into
     * a single batch (up to maxBatchSize), and feeds to the fact extractor.
     */
    @Scheduled(fixedDelayString = "${agent.log-worker.interval-ms:10000}")
    @Transactional
    public void run() {
        try {
            int remaining = maxBatchSize;
            List<RawLog> batch = new ArrayList<>(maxBatchSize);
            Pageable page;

            // 1. Pull agent logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                var agentLogs = agentLogRepository.findByProcessedFalseOrderByTimestampAsc(page);
                batch.addAll(agentLogs);
                remaining -= agentLogs.size();
            }

            // 2. Pull event captures
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                var eventCaptures = eventCaptureRepository.findByProcessedFalseOrderByTimestampAsc(page);
                batch.addAll(eventCaptures);
                remaining -= eventCaptures.size();
            }

            // 3. Pull event execution logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                var executionLogs = eventExecutionLogRepository.findByProcessedFalseOrderByTimestampAsc(page);
                batch.addAll(executionLogs);
                remaining -= executionLogs.size();
            }

            // 4. Pull notification attempt logs
            if (remaining > 0) {
                page = PageRequest.of(0, remaining);
                var notificationLogs = notificationAttemptLogRepository
                        .findByProcessedFalseOrderByTimestampAsc(page);
                batch.addAll(notificationLogs);
            }

            if (batch.isEmpty()) {
                return; // nothing to process
            }

            log.info("LogToMemoryAgentWorker: processing batch of {} logs", batch.size());

            // Feed to fact extractor
            factExtractor.extractFacts(batch);

            // Mark all as processed
            Instant now = Instant.now();
            for (RawLog rawLog : batch) {
                rawLog.setProcessed(true);
                rawLog.setProcessedAt(now);
            }

            // Persist updated flags (each repo handles its own type)
            // We can use saveAll on the individual repos, but since RawLog
            // is abstract and each subtype has its own table, we rely on the
            // @Transactional flush. The entities are already managed within
            // the transaction, so the dirty-check will persist the updates.

            log.info("LogToMemoryAgentWorker: marked {} logs as processed", batch.size());

        } catch (Exception e) {
            log.error("LogToMemoryAgentWorker: error processing batch", e);
        }
    }
}
