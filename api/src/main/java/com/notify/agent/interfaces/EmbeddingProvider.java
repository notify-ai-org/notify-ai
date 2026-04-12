package com.notify.agent.interfaces;

import com.notify.agent.records.EmbeddingResult;
import reactor.core.publisher.Mono;

import java.util.List;

public interface EmbeddingProvider {
    Mono<List<EmbeddingResult>> embedBatch(String model, List<String> texts, List<String> textHashes);
}
