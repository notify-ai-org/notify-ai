package com.example.agent.service;

import java.util.concurrent.atomic.AtomicBoolean;

public class LogToMemoryAgentWorker implements Runnable {

    private final BlockingQueue<RawLogEnvelope> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final LogPreprocessor preprocessor;
    private final LlmFactExtractor factExtractor;
    private final FactStore factStore;
    private final MemoryPageAssembler pageAssembler;
    private final MemoryStore memoryStore;

    private final int maxBatchSize;
    private final long flushDelayMs;

    public LogToMemoryAgentWorker(
            BlockingQueue<RawLogEnvelope> queue,
            LogPreprocessor preprocessor,
            LlmFactExtractor factExtractor,
            FactStore factStore,
            MemoryPageAssembler pageAssembler,
            MemoryStore memoryStore,
            int maxBatchSize,
            long flushDelayMs) {

        this.queue = queue;
        this.preprocessor = preprocessor;
        this.factExtractor = factExtractor;
        this.factStore = factStore;
        this.pageAssembler = pageAssembler;
        this.memoryStore = memoryStore;
        this.maxBatchSize = maxBatchSize;
        this.flushDelayMs = flushDelayMs;
    }

    @Override
    public void run() {
        List<RawLogEnvelope> batch = new ArrayList<>(maxBatchSize);
        long lastFlush = System.currentTimeMillis();

        while (running.get()) {
            try {
                RawLogEnvelope item = queue.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (item != null) batch.add(item);

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
            try { processBatch(batch); } catch (Exception ignored) {}
        }
    }

    public void shutdown() {
        running.set(false);
    }

    private void processBatch(List<RawLogEnvelope> rawBatch) {
        // 1) preprocess
        List<PreprocessedLog> cleaned = rawBatch.stream()
                .map(preprocessor::clean)
                .filter(Objects::nonNull)
                .toList();

        if (cleaned.isEmpty()) return;

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
