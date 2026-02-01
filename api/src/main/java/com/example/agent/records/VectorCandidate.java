package com.example.agent.records;

public record VectorCandidate(
        MemoryPage page,
        double similarity
) {
}
