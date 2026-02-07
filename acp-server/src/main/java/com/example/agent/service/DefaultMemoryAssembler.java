package com.example.agent.service;

import com.example.agent.MemoryPageRepository;
import com.example.agent.enums.PageType;
import com.example.agent.interfaces.MemoryAssembler;
import com.example.agent.records.EntityRef;
import com.example.agent.records.Fact;
import com.example.agent.records.MemoryPage;
import com.example.agent.records.VectorCandidate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultMemoryAssembler implements MemoryAssembler {

    private final Duration windowSize;
    private final Duration inactivityTimeout;
    private final int maxFactsPerPage;

    private final MemoryPageRepository pageRepo;

    public DefaultMemoryAssembler(
            Duration windowSize,
            Duration inactivityTimeout,
            int maxFactsPerPage,
            MemoryPageRepository pageRepo) {

        this.windowSize = windowSize;
        this.inactivityTimeout = inactivityTimeout;
        this.maxFactsPerPage = maxFactsPerPage;
        this.pageRepo = pageRepo;
    }

    @Override
    public String incrementalUpdate(MemoryPage page, Fact newFact) {
        if (page.summary() == null) {
            return "- " + renderFact(newFact);
        }
        return page.summary() + "\n- " + renderFact(newFact);
    }

    @Override
    public List<MemoryPage> buildPages(List<Fact> newFacts) {
        if (newFacts == null || newFacts.isEmpty()) {
            return List.of();
        }

        // 1. Group facts by namespace
        Map<String, List<Fact>> byNamespace = newFacts.stream()
                .collect(Collectors.groupingBy(Fact::factType));

        List<MemoryPage> updatedPages = new ArrayList<>();

        for (var entry : byNamespace.entrySet()) {
            String namespace = entry.getKey();
            List<Fact> facts = entry.getValue();

            // Sort facts by time (important for determinism)
            facts.sort(Comparator.comparing(f -> f.observedAt()));

            for (Fact fact : facts) {
                MemoryPage page = findOrCreatePage(namespace, fact);
                appendFact(page, fact);
                updatedPages.add(page);
            }
        }

        return updatedPages;
    }

    private void appendFact(MemoryPage page, Fact fact) {
        page.updatedAt() = fact.observedAt();
        // Incremental summary (cheap) or defer until close
        page.summary() = incrementalUpdate(page, fact);
        pageRepo.save(page);
    }

    @Override
    public String summarize(MemoryPage page) {
        // TODO: call LLM to produce a human-readable summary
        return "Summary of facts in " + page.namespace + " (window " + page.windowStart + ")";
    }

    @Override
    public MemoryPage findOrCreatePage(String namespace, Fact fact) {
        Instant now = fact.observedAt();
        Instant windowStart = alignToWindow(now);

        // Try to find an open page
        Optional<MemoryPage> open = pageRepo.findOpenPage(namespace, windowStart);

        if (open.isPresent()) {
            MemoryPage page = open.get();
            if (shouldClose(page, fact)) {
                closePage(page);
            } else {
                return page;
            }
        }

        // Create a new page
        MemoryPage page = new MemoryPage();
        page.pageId = generatePageId(namespace, windowStart);
        page.tenantId = fact.tenantId;
        page.namespace = namespace;
        page.windowStart = windowStart;
        page.windowEnd = windowStart.plus(windowSize);
        page.createdAt = Instant.now();

        pageRepo.save(page);
        return page;
    }

    @Override
    public List<VectorCandidate> search(
            String tenantId,
            String queryText,
            List<EntityRef> scope,
            Set<PageType> pageTypes,
            Instant since,
            int k) {
        return null;
    }

    private Instant alignToWindow(Instant time) {
        long epoch = time.getEpochSecond();
        long windowSeconds = windowSize.getSeconds();
        long aligned = (epoch / windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(aligned);
    }

    private String generatePageId(String namespace, Instant windowStart) {
        return namespace + ":" + windowStart.toString();
    }

    private boolean shouldClose(MemoryPage page, Fact incoming) {

        Instant lastFactTime = page.lastUpdatedAt;
        if (lastFactTime != null &&
                Duration.between(lastFactTime, incoming.observedAt)
                        .compareTo(inactivityTimeout) > 0) {
            return true;
        }

        // Explicit boundary predicates
        if ("completed".equalsIgnoreCase(incoming.predicate) ||
                "dead_lettered".equalsIgnoreCase(incoming.predicate)) {
            return true;
        }

        return false;
    }

    private void closePage(MemoryPage page) {
        page.closedAt = Instant.now();
        pageRepo.save(page);

        // Optionally: finalize summary
        page.summary = summarize(page);
        pageRepo.save(page);
    }

}
