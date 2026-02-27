package com.example.agent.records;

import java.time.Instant;
import java.util.List;

public record Fact(
        String factId,
        String factType,
        String sentence,
        Instant observedAt,
        double confidence,
        double importance,
        int ttlDays,
        List<String> sourceEventIds,
        String correlationId) {
}
