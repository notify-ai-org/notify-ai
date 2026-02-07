package com.example.agent.records;

import com.example.agent.enums.PageType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public record MemoryPage(
                @Id String pageId,
                PageType pageType,
                String summary, // already “memory-shaped”, not raw logs
                Instant timestamp,
                double importance,
                double confidence,
                Instant createdAt,
                Instant updatedAt,
                Set<String> tags,
                List<EntityRef> scope,
                String rawRef // pointer to cold storage (not inserted verbatim)
) {
}
