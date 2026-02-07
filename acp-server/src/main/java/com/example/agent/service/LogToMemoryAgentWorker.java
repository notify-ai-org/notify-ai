package com.example.agent.service;

import com.example.agent.FactRepository;
import com.example.agent.MemoryPageRepository;
import com.example.agent.consumers.FactConsumer;
import com.example.agent.interfaces.MemoryAssembler;
import com.example.agent.records.Fact;
import com.example.agent.records.MemoryPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogToMemoryAgentWorker implements Runnable {

    private final BlockingQueue<RawLog> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final FactConsumer factExtractor;
    private final FactRepository factStore;
    private final MemoryAssembler pageAssembler;
    private final MemoryPageRepository memoryStore;

    private final int maxBatchSize;
    private final long flushDelayMs;

    public LogToMemoryAgentWorker(
            BlockingQueue<RawLog> queue,
            FactConsumer factExtractor,
            FactRepository factStore,
            MemoryAssembler pageAssembler,
            MemoryPageRepository memoryStore,
            int maxBatchSize,
            long flushDelayMs) {

        this.queue = queue;
        this.factExtractor = factExtractor;
        this.factStore = factStore;
        this.pageAssembler = pageAssembler;
        this.memoryStore = memoryStore;
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
        // 1) preprocess
        List<PreprocessedLog> cleaned = rawBatch.stream()
                .map(preprocessor::clean)
                .filter(Objects::nonNull)
                .toList();

        if (cleaned.isEmpty())
            return;

        // 2) LLM → facts
        List<Fact> facts = factExtractor.extractFacts(cleaned);

        // 3) dedupe + persist facts
        List<Fact> newFacts = factStore.upsertDedup(facts);

        // 4) assemble pages (windowed + topic grouping)
        List<MemoryPage> pages = pageAssembler.buildPages(newFacts);

        // 5) persist pages
        memoryStore.saveAll(pages);
    }
}
