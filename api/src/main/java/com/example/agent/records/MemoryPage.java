package com.example.agent.records;

import com.example.agent.enums.PageType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record MemoryPage(
        String pageId,
        PageType pageType,
        String summary,              // already “memory-shaped”, not raw logs
        Instant timestamp,
        double importance,
        double confidence,
        Set<String> tags,
        List<EntityRef> scope,
        String rawRef                // pointer to cold storage (not inserted verbatim)
) {
}
