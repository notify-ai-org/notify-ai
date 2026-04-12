package com.notify.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.notify.agent.NotificationWorker.WorkerStatus;
import com.notify.agent.models.ConnectorMetrics;
import com.notify.agent.models.NotificationAttemptLog;
import com.notify.agent.models.NotificationJob;
import com.notify.agent.models.WorkerSnapshot;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import com.notify.agent.annotations.ManagedConfiguration;

@Component
@RequiredArgsConstructor
public class DispatcherWorkerPool implements Runnable {

    @Data
    public class DispatcherProperties {

        @ManagedConfiguration(key = "dispatcher.min-workers")
        private int minWorkers = 2;

        @ManagedConfiguration(key = "dispatcher.max-workers")
        private int maxWorkers = 20;

        @ManagedConfiguration(key = "dispatcher.idle-worker-ttl-seconds")
        private int idleWorkerTtlSeconds = 30;

        @ManagedConfiguration(key = "dispatcher.poll-batch-size")
        private int pollBatchSize = 10;

        @ManagedConfiguration(key = "dispatcher.log-flush-interval-ms")
        private long logFlushIntervalMs = 5000;

        @ManagedConfiguration(key = "dispatcher.log-buffer-size")
        private int logBufferSize = 50;

        @ManagedConfiguration(key = "dispatcher.retry-max-attempts")
        private int retryMaxAttempts = 5;

        @ManagedConfiguration(key = "dispatcher.retry-backoff-millis")
        private long retryBackoffMillis = 2000;
    }

    private final DispatcherProperties properties = new DispatcherProperties();

    public DispatcherProperties getProperties() {
        return properties;
    }

    private final ApplicationContext context;

    private final WorkerSnapshotRepository workerSnapshotRepository;

    private final ExecutorService executor;

    private final Set<NotificationWorker> workers = ConcurrentHashMap.newKeySet();

    // Log buffer for NotificationAttemptLog, uses AtomicReference for safe
    // concurrent updates.
    private final AtomicReference<List<NotificationAttemptLog>> logBuffer = new AtomicReference<>(new ArrayList<>());

    // Reference to the NotificationAttemptLog repository (assume injected via
    // constructor if necessary)
    private final NotificationAttemptLogRepository logRepo;

    private final Map<String, ConnectorMetrics> metricsMap = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(DispatcherWorkerPool.class);

    @PostConstruct
    public void init() {
        List<WorkerSnapshot> workers = workerSnapshotRepository.findByStatus(WorkerStatus.UNAVAILABLE.name());
        // Initialize minimum amount of workers
        // Initialize workers using existing snapshots; start at least minWorkers
        if (workers != null && !workers.isEmpty()) {
            for (WorkerSnapshot snapshot : workers) {
                NotificationWorker worker = context.getBean(NotificationWorker.class);
                worker.setLogBuffer(logBuffer);
                // load state from snapshot if needed
                worker.loadState(snapshot);
                executor.submit(worker);
                this.workers.add(worker);
            }
        }
        // Ensure we still meet the minimum worker count
        if (this.workers.size() < properties.getMinWorkers()) {
            int additional = properties.getMinWorkers() - this.workers.size();
            scaleToFit(additional);
        }
        executor.submit(this);
    }

    public void assign(NotificationJob job) {
        // scaleToFit(1)
        scaleToFit(1);

        // get available worker, wait till timeout
        NotificationWorker availableWorker = findAvailableWorker(5, TimeUnit.SECONDS);
        if (availableWorker == null) {
            throw new RuntimeException("No available worker found within timeout");
        }

        logger.debug("Available worker: {} for job: {}", availableWorker.workerId, job.getId());

        // set the notification job in the worker
        // notify the waiting worker thread
        if (!availableWorker.assignJob(job)) {
            logger.debug("Failed to assign job to worker: {} for job: {}", availableWorker.workerId, job.getId());
            throw new RuntimeException("Failed to assign job to worker");
        }
    }

    private NotificationWorker findAvailableWorker(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        while (System.nanoTime() < deadline) {
            for (NotificationWorker worker : workers) {
                if (worker.isAvailable()) {
                    return worker;
                }
            }

            // If no worker is available, wait a bit and try again
            try {
                Thread.sleep(100); // Wait 100ms before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null;
    }

    public void scaleToFit(int incomingJobs) {
        // Ensure we have at least minWorkers, and scale up to handle incoming jobs
        // Cap at maxWorkers
        int targetSize = Math.max(properties.getMinWorkers(), workers.size() + incomingJobs);
        int required = Math.min(properties.getMaxWorkers(), targetSize);

        for (int i = 0; i < required; i++) {
            NotificationWorker worker = context.getBean(NotificationWorker.class);
            worker.setLogBuffer(logBuffer);
            workers.add(worker);
            executor.submit(worker);
        }
    }

    // Removes workers that have been idle for longer than the specified threshold
    // (in milliseconds)
    public void removeIdleWorkers() {
        Instant now = Instant.now();
        // Use iterator to safely remove from workers list during iteration
        workers.removeIf(worker -> {
            if (worker.isAvailable()) {
                Instant lastActive = worker.getLastActiveAt();
                if (lastActive == null) {
                    return false; // Cannot determine idle time
                }
                long idleMillis = java.time.Duration.between(lastActive, now).toMillis();
                // Only remove workers if above minWorkers
                if (idleMillis >= properties.idleWorkerTtlSeconds * 1000L
                        && workers.size() > properties.getMinWorkers()) {
                    worker.shutdown();
                    // delete snapshot?
                    return true;
                }
            }
            return false;
        });
    }

    @PreDestroy
    public void shutdown() {
        workers.forEach(NotificationWorker::shutdown);
        executor.shutdownNow();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(properties.getLogFlushIntervalMs());
                removeIdleWorkers();
                // Update metrics and persist WorkerSnapshot for each worker
                for (NotificationWorker worker : workers) {
                    metricsMap.put(worker.getWorkerId(), worker.getMetrics());

                    // Save worker snapshot
                    NotificationJob currentJob = worker.getCurrentJob();
                    Instant lastActive = worker.getLastActiveAt() != null ? worker.getLastActiveAt() : Instant.now();

                    WorkerSnapshot snapshot = workerSnapshotRepository.findByWorkerId(worker.getWorkerId())
                            .orElse(new WorkerSnapshot(
                                    worker.getWorkerId(),
                                    lastActive,
                                    worker.getStatus().toString(),
                                    currentJob == null ? null : currentJob.getId()));

                    // Always update fields in case we retrieved an existing snapshot
                    snapshot.setLastActiveAt(lastActive);
                    snapshot.setStatus(worker.getStatus().toString());
                    snapshot.setJobId(currentJob == null ? null : currentJob.getId());

                    workerSnapshotRepository.save(snapshot);
                }
                List<NotificationAttemptLog> toFlush = logBuffer.getAndSet(new ArrayList<>());
                if (!toFlush.isEmpty()) {
                    logRepo.saveAll(toFlush);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                logger.info("DispatcherWorkerPool interrupted, shutting down");
                break;
            } catch (Throwable t) {
                // Optionally log error
                logger.error("Error in DispatcherWorkerPool", t);
            }
        }
    }
}
