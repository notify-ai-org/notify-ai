package com.example.agent.interfaces;

import com.example.agent.records.EmbeddingResult;
import reactor.core.publisher.Mono;

import java.util.List;

public interface EmbeddingProvider {
    Mono<List<EmbeddingResult>> embedBatch(String model, List<String> texts, List<String> textHashes);
}
