package com.notify.agent.records;

import com.notify.agent.enums.PageType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public record MemoryPage(
        @Id String pageId,
        String namespace,
        String correlationId,
        PageType pageType,
        String summary,
        String severityMax,
        Instant timestamp,
        double importance,
        double confidence,
        Instant createdAt,
        Instant updatedAt,
        Set<String> tags,
        List<EntityRef> scope,
        String rawRef,
        float[] embedding) {

    /** Backward compatibility constructor for the old 11-field signature. */
    public MemoryPage(String pageId, PageType pageType, String summary, Instant timestamp,
            double importance, double confidence, Instant createdAt, Instant updatedAt,
            Set<String> tags, List<EntityRef> scope, String rawRef) {
        this(pageId, null, null, pageType, summary, null, timestamp,
                importance, confidence, createdAt, updatedAt, tags, scope, rawRef, null);
    }
}
