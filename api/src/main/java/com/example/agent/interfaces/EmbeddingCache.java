package com.example.agent.interfaces;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface EmbeddingCache {
    Mono<float[]> get(String tenantId, String model, String schemaVersion, String textHash);

    Mono<Void> put(String tenantId, String model, String schemaVersion, String textHash, float[] vector, Duration ttl);
}
