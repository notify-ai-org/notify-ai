package com.notify.agent.records;

public record EmbeddingResult(
            String model,
            String textHash,
            float[] vector
    ) {}