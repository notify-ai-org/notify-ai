package com.example.agent.service;

import com.example.agent.consumers.FactConsumer;
import com.example.agent.models.RawLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class LogToMemoryAgentWorker implements Runnable {

    private final BlockingQueue<RawLog> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final FactConsumer factExtractor;

    private final int maxBatchSize;
    private final long flushDelayMs;

    public LogToMemoryAgentWorker(
            BlockingQueue<RawLog> queue,
            FactConsumer factExtractor,
            int maxBatchSize,
            long flushDelayMs) {

        this.queue = queue;
        this.factExtractor = factExtractor;
        this.maxBatchSize = maxBatchSize;
        this.flushDelayMs = flushDelayMs;
    }

    @Override
    public void run() {
        List<RawLog> batch = new ArrayList<>(maxBatchSize);
        long lastFlush = System.currentTimeMillis();

        while (running.get()) {
            try {
                RawLog item = queue.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (item != null)
                    batch.add(item);

                boolean sizeFlush = batch.size() >= maxBatchSize;
                boolean timeFlush = !batch.isEmpty() && (now - lastFlush) >= flushDelayMs;

                if (sizeFlush || timeFlush) {
                    processBatch(batch);
                    batch.clear();
                    lastFlush = now;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // never die
                // log.error("LogToMemoryAgentWorker loop error", e);
            }
        }

        // drain on shutdown
        if (!batch.isEmpty()) {
            try {
                processBatch(batch);
            } catch (Exception ignored) {
            }
        }
    }

    public void shutdown() {
        running.set(false);
    }

    private void processBatch(List<RawLog> rawBatch) {
        // Filter out null entries
        List<RawLog> cleaned = rawBatch.stream()
                .filter(Objects::nonNull)
                .toList();

        if (cleaned.isEmpty())
            return;

        // Trigger asynchronous LLM-based fact extraction
        factExtractor.extractFacts(cleaned);

        // Note: As fact extraction is now asynchronous, page assembly and
        // persistence must be handled in the FactConsumer completion or a separate
        // process.
    }
}
