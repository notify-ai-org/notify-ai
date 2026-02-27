package com.example.agent.service;

import com.example.agent.interfaces.EmbeddingCache;
import com.example.agent.interfaces.EmbeddingProvider;
import com.example.agent.records.EmbeddingRequest;
import com.example.agent.records.EmbeddingResult;
import com.example.agent.records.MemoryPage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final EmbeddingProvider embeddingProvider;

    private final EmbeddingCache embeddingCache;

    @Value("${embedding.max-batch-size:10}")
    private int maxBatchSize;

    @Value("${embedding.max-wait:100ms}")
    private Duration maxWait;

    @Value("${embedding.schema-version:v1}")
    private String schemaVersion;

    @Value("${embedding.cache-ttl:24h}")
    private Duration cacheTtl;

    private final reactor.core.publisher.Sinks.Many<Pending> sink = reactor.core.publisher.Sinks.many().unicast()
            .onBackpressureBuffer();

    public EmbeddingService(EmbeddingProvider embeddingProvider, EmbeddingCache embeddingCache) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingCache = embeddingCache;
    }

    @PostConstruct
    void init() {
        startLoop();
    }

    private void startLoop() {
        sink.asFlux()
                .windowTimeout(maxBatchSize, maxWait)
                .flatMap(window -> window.collectList())
                .filter(list -> !list.isEmpty())
                .flatMap(this::processBatch)
                .subscribe(); // managed by Spring lifecycle in real app
    }

    public Mono<EmbeddingResult> embedOne(EmbeddingRequest req) {
        return Mono.create(sinkMono -> {
            var pending = new Pending(req, sinkMono);
            var res = sink.tryEmitNext(pending);
            if (res.isFailure()) {
                sinkMono.error(new RuntimeException("Embedding batch queue full or terminated: " + res));
            }
        });
    }

    private Mono<Void> processBatch(List<Pending> batch) {
        // group by model so each provider call uses one model
        Map<String, List<Pending>> byModel = batch.stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p.req.model()));

        return Flux.fromIterable(byModel.entrySet())
                .flatMap(e -> callProvider(e.getKey(), e.getValue()))
                .then();
    }

    private Mono<Void> callProvider(String model, List<Pending> pendings) {
        List<String> texts = pendings.stream().map(p -> p.req.text()).toList();
        List<String> hashes = pendings.stream().map(p -> p.req.textHash()).toList();

        return embeddingProvider.embedBatch(model, texts, hashes)
                .doOnNext(results -> {
                    Map<String, EmbeddingResult> maxp = results.stream()
                            .collect(java.util.stream.Collectors.toMap(EmbeddingResult::textHash, r -> r));

                    for (Pending p : pendings) {
                        EmbeddingResult r = maxp.get(p.req.textHash());
                        if (r != null)
                            p.sink.success(r);
                        else
                            p.sink.error(new RuntimeException("Missing embedding result for hash " + p.req.textHash()));
                    }
                })
                .doOnError(ex -> pendings.forEach(p -> p.sink.error(ex)))
                .then();
    }

    private static final class Pending {
        final EmbeddingRequest req;
        final reactor.core.publisher.MonoSink<EmbeddingResult> sink;

        Pending(EmbeddingRequest req, reactor.core.publisher.MonoSink<EmbeddingResult> sink) {
            this.req = req;
            this.sink = sink;
        }
    }

    public String build(MemoryPage page) {
        // Avoid volatile data: timestamps, random IDs in text
        return """
                Namespace: %s
                Severity: %s

                Summary:
                %s
                """.formatted(
                nullToNA(page.namespace()),
                nullToNA(page.severityMax()),
                sanitize(page.summary())).trim();
    }

    private String sanitize(String s) {
        if (s == null)
            return "";
        // Light normalization: collapse whitespace
        return s.replaceAll("\\s+", " ").trim();
    }

    private String nullToNA(String s) {
        return (s == null || s.isBlank()) ? "NA" : s;
    }

    public static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final List<String> models = List.of("text-embedding-3-large", "text-embedding-3-small");

    public Mono<float[]> embed(MemoryPage page) {
        String text = build(page);
        String textHash = sha256(text);

        // try models in order
        return Flux.fromIterable(models)
                .concatMap(model -> tryModel(page, text, textHash, model)
                        .onErrorResume(ex -> Mono.empty()))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("All embedding models failed")));
    }

    private Mono<float[]> tryModel(MemoryPage page, String text, String textHash, String model) {
        return embeddingCache.get(model, schemaVersion, textHash).switchIfEmpty(
                embedOne(new EmbeddingRequest(
                        page.namespace(),
                        page.pageId(),
                        text,
                        model,
                        schemaVersion,
                        textHash))
                        .map(EmbeddingResult::vector)
                        .flatMap(vec -> embeddingCache
                                .put(model, schemaVersion, textHash, vec, cacheTtl).thenReturn(vec)));
    }
}
