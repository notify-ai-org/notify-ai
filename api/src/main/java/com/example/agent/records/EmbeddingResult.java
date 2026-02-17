package com.example.agent.records;

public record EmbeddingResult(
            String model,
            String textHash,
            float[] vector
    ) {}