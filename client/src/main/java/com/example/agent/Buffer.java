package com.example.agent;

import com.example.agent.sdk.dto.ClassModelDto;
import com.example.agent.sdk.dto.EventCaptureDto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a batch of records to be sent to acp-server. Flushes when either
 * batch size or timeout is reached. Configurable batch size and flush timeout.
 */
public class Buffer {

    public enum RecordType {
        VOCABULARY,   // payload: List<ClassModelDto>
        RULE,         // payload: Map with eventName, ruleName, ruleDescription, payload
        EVENT_CAPTURE // payload: EventCaptureDto
    }

    public static final class Record {
        private final RecordType type;
        private final Object payload;

        public Record(RecordType type, Object payload) {
            this.type = type;
            this.payload = payload;
        }
        public RecordType getType() { return type; }
        public Object getPayload() { return payload; }
    }

    private final BlockingQueue<Record> queue = new LinkedBlockingQueue<>();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final int batchSize;
    private final long flushTimeoutMs;
    private volatile long lastFlushAt;

    public Buffer(int batchSize, long flushTimeoutMs) {
        this.batchSize = batchSize <= 0 ? 100 : batchSize;
        this.flushTimeoutMs = flushTimeoutMs <= 0 ? 5_000 : flushTimeoutMs;
        this.lastFlushAt = System.currentTimeMillis();
    }

    public void add(RecordType type, Object payload) {
        queue.add(new Record(type, payload));
    }

    public void addVocabulary(List<ClassModelDto> list) {
        add(RecordType.VOCABULARY, new ArrayList<>(list));
    }

    public void addRule(String eventName, String ruleName, String ruleDescription, java.util.Map<String, Object> payload) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("eventName", eventName);
        m.put("ruleName", ruleName);
        m.put("ruleDescription", ruleDescription);
        if (payload != null) m.put("payload", payload);
        add(RecordType.RULE, m);
    }

    public void addEventCapture(EventCaptureDto dto) {
        add(RecordType.EVENT_CAPTURE, dto);
    }

    /**
     * Drain up to batchSize records into the given list. Returns the number drained.
     */
    public int drainTo(List<Record> out, int max) {
        return queue.drainTo(out, max <= 0 ? batchSize : max);
    }

    /**
     * Take one record, blocking until available. For use by a single dispatcher thread.
     */
    public Record take() throws InterruptedException {
        return queue.take();
    }

    public int size() { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }

    public int getBatchSize() { return batchSize; }
    public long getFlushTimeoutMs() { return flushTimeoutMs; }

    public boolean shouldFlushBySize() { return queue.size() >= batchSize; }
    public boolean shouldFlushByTimeout() {
        return (System.currentTimeMillis() - lastFlushAt) >= flushTimeoutMs;
    }

    public void markFlushed() { lastFlushAt = System.currentTimeMillis(); }
}
