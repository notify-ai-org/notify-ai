package com.example.agent;

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

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.example.agent.NotificationWorker.WorkerStatus;
import com.example.agent.models.ConnectorMetrics;
import com.example.agent.models.NotificationAttemptLog;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.WorkerSnapshot;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DispatcherWorkerPool implements Runnable {

    @Data
    public class DispatcherProperties {

        private int minWorkers = 2;
        private int maxWorkers = 20;
        private int idleWorkerTtlSeconds = 30;
        private int pollBatchSize = 10;

        // Configurable flush interval (ms) and buffer size
        private long logFlushIntervalMs = 5000; // e.g., configurable via @Value or DispatcherProperties
        private int logBufferSize = 50; // e.g., configurable via @Value or DispatcherProperties

        private int retryMaxAttempts = 5;
        private long retryBackoffMillis = 2000;
    }

    private final DispatcherProperties properties = new DispatcherProperties();

    public DispatcherProperties getProperties() {
        return properties;
    }

    private final ApplicationContext context;

    private final WorkerSnapshotRepository workerSnapshotRepository;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Set<NotificationWorker> workers = ConcurrentHashMap.newKeySet();

    // Log buffer for NotificationAttemptLog, uses AtomicReference for safe
    // concurrent updates.
    private final AtomicReference<List<NotificationAttemptLog>> logBuffer = new AtomicReference<>(new ArrayList<>());

    // Reference to the NotificationAttemptLog repository (assume injected via
    // constructor if necessary)
    private final NotificationAttemptLogRepository logRepo;

    private final Map<String, ConnectorMetrics> metricsMap = new HashMap<>();

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

        // set the notification job in the worker
        // notify the waiting worker thread
        if (!availableWorker.assignJob(job)) {
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

        while (workers.size() < required) {
            NotificationWorker worker = context.getBean(NotificationWorker.class);
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
                long idleMillis = java.time.Duration.between(worker.getLastActiveAt(), now).toMillis();
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
        while (true) {
            try {
                Thread.sleep(5000);
                removeIdleWorkers();
                // Update metrics and persist WorkerSnapshot for each worker
                for (NotificationWorker worker : workers) {
                    metricsMap.put(worker.getWorkerId(), worker.getMetrics());

                    // Save worker snapshot
                    WorkerSnapshot snapshot = workerSnapshotRepository.findByWorkerId(worker.getWorkerId())
                            .orElse(new WorkerSnapshot(
                                    worker.getWorkerId(),
                                    worker.getLastActiveAt(),
                                    worker.getStatus().toString(),
                                    worker.getCurrentJob().getId()));

                    workerSnapshotRepository.save(snapshot);
                }
                if (logBuffer.get().size() < properties.getLogBufferSize())
                    return;
                List<NotificationAttemptLog> toFlush = logBuffer.getAndSet(new ArrayList<>());
                if (!toFlush.isEmpty()) {
                    logRepo.saveAll(toFlush);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // Optionally log error
            }
        }
    }
}
