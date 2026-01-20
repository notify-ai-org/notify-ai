package com.example.agent;

import com.example.agent.sdk.dto.ClassModelDto;
import com.example.agent.sdk.dto.EventCaptureDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Thread that pulls records from the Buffer and sends them to acp-server (or
 * notification engine) based on RecordType. Applies routing by type only.
 * Fetches a new token via the provided supplier when the current one is expired or 401.
 */
public class Dispatcher implements Runnable {

    private final Buffer buffer;
    private final AcpServerClient acpClient;
    private final Supplier<String> tokenSupplier;
    private final Runnable onTokenExpired;
    private volatile boolean running = true;

    public Dispatcher(Buffer buffer, AcpServerClient acpClient,
                      Supplier<String> tokenSupplier, Runnable onTokenExpired) {
        this.buffer = buffer;
        this.acpClient = acpClient;
        this.tokenSupplier = tokenSupplier;
        this.onTokenExpired = onTokenExpired != null ? onTokenExpired : () -> {};
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        List<Buffer.Record> batch = new ArrayList<>();
        List<EventCaptureDto> eventBatch = new ArrayList<>();

        while (running) {
            try {
                int n = buffer.drainTo(batch, buffer.getBatchSize());
                if (n == 0) {
                    Thread.sleep(100);
                    continue;
                }

                for (Buffer.Record r : batch) {
                    switch (r.getType()) {
                        case VOCABULARY:
                            @SuppressWarnings("unchecked")
                            List<ClassModelDto> vocab = (List<ClassModelDto>) r.getPayload();
                            postWithAuthRetry(() -> acpClient.postVocabulary(vocab, tokenSupplier.get()));
                            break;
                        case RULE:
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rule = (Map<String, Object>) r.getPayload();
                            postWithAuthRetry(() -> acpClient.postRule(rule, tokenSupplier.get()));
                            break;
                        case EVENT_CAPTURE:
                            eventBatch.add((EventCaptureDto) r.getPayload());
                            break;
                    }
                }

                if (!eventBatch.isEmpty()) {
                    List<EventCaptureDto> toSend = new ArrayList<>(eventBatch);
                    eventBatch.clear();
                    postWithAuthRetry(() -> acpClient.postEventCaptures(toSend, tokenSupplier.get()));
                }

                buffer.markFlushed();
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
                break;
            } catch (Exception e) {
                // log and continue
                e.printStackTrace();
            }
        }
    }

    @FunctionalInterface
    private interface PostOp { int run() throws Exception; }

    private void postWithAuthRetry(PostOp op) throws Exception {
        try {
            op.run();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                onTokenExpired.run();
                op.run();
            } else {
                throw e;
            }
        }
    }
}
