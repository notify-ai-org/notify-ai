package com.example.agent.service;

import com.example.agent.MemoryPageRepository;
import com.example.agent.enums.PageType;
import com.example.agent.interfaces.MemoryAssembler;
import com.example.agent.records.*;
import com.example.agent.annotations.ManagedConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultMemoryAssembler implements MemoryAssembler {

    @ManagedConfiguration(key = "agent.memory.window-size")
    private Duration windowSize;

    @ManagedConfiguration(key = "agent.memory.inactivity-timeout")
    private Duration inactivityTimeout;

    @ManagedConfiguration(key = "agent.memory.max-facts")
    private int maxFactsPerPage;

    private final EmbeddingService embeddingService;
    private final MemoryPageRepository pageRepo;

    public DefaultMemoryAssembler(
            @Value("${agent.memory.window-size:1h}") Duration windowSize,
            @Value("${agent.memory.inactivity-timeout:30m}") Duration inactivityTimeout,
            @Value("${agent.memory.max-facts:50}") int maxFactsPerPage,
            MemoryPageRepository pageRepo,
            EmbeddingService embeddingService) {

        this.windowSize = windowSize;
        this.inactivityTimeout = inactivityTimeout;
        this.maxFactsPerPage = maxFactsPerPage;
        this.pageRepo = pageRepo;
        this.embeddingService = embeddingService;
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

        // 1. Group facts by namespace (fallback to "general" if factType is null)
        Map<String, List<Fact>> byNamespace = newFacts.stream()
                .collect(Collectors.groupingBy(f -> f.factType() != null ? f.factType() : "general"));

        List<MemoryPage> updatedPages = new ArrayList<>();

        for (var entry : byNamespace.entrySet()) {
            String namespace = entry.getKey();
            List<Fact> facts = entry.getValue();

            // Sort facts by time (important for determinism)
            facts.sort(Comparator.comparing(Fact::observedAt));

            for (Fact fact : facts) {
                MemoryPage page = findOrCreatePage(namespace, fact);
                MemoryPage updatedPage = appendFact(page, fact);
                updatedPages.add(updatedPage);
            }
        }

        return updatedPages;
    }

    public MemoryPage appendFact(MemoryPage page, Fact fact) {
        // Create a new MemoryPage with updated fields (records are immutable)
        String updatedSummary = incrementalUpdate(page, fact);

        // Create a temporary page to generate embedding
        MemoryPage tempPage = new MemoryPage(
                page.pageId(),
                page.namespace(),
                fact.correlationId(),
                page.pageType(),
                updatedSummary,
                page.severityMax(),
                page.timestamp(),
                page.importance(),
                page.confidence(),
                page.createdAt(),
                fact.observedAt(), // updatedAt
                page.tags(),
                page.scope(),
                page.rawRef(),
                null // embedding will be generated next
        );

        // Generate embedding for the updated page
        float[] embedding = embeddingService.embed(tempPage).block();

        MemoryPage updatedPage = new MemoryPage(
                page.pageId(),
                page.namespace(),
                fact.correlationId(),
                page.pageType(),
                updatedSummary,
                page.severityMax(),
                page.timestamp(),
                page.importance(),
                page.confidence(),
                page.createdAt(),
                fact.observedAt(), // updatedAt
                page.tags(),
                page.scope(),
                page.rawRef(),
                embedding); // Use float[] directly

        pageRepo.upsert(updatedPage, Duration.ofDays(30));
        return updatedPage;
    }

    @Override
    public String summarize(MemoryPage page) {
        if (page.summary() == null || page.summary().isBlank()) {
            return "No facts to summarize.";
        }
        
        // Simpler approach: Return the raw chronologically appended bullet points
        // as the final memory block instead of requiring a secondary LLM step.
        return page.summary();
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

        // Create a new page using the full record constructor
        String pageId = generatePageId(namespace, windowStart);
        Instant createdAt = Instant.now();

        MemoryPage page = new MemoryPage(
                pageId,
                namespace,
                fact.correlationId(),
                PageType.EPISODIC, // default page type for fact streams
                null, // summary - will be built incrementally
                null, // severityMax
                windowStart, // timestamp
                0.5, // default importance
                0.8, // default confidence
                createdAt,
                createdAt, // updatedAt initially same as createdAt
                new HashSet<>(), // empty tags
                new ArrayList<>(), // empty scope
                null, // no raw ref initially
                null // no embedding initially
        );

        pageRepo.upsert(page, Duration.ofDays(30));
        return page;
    }

    @Override
    public List<VectorCandidate> search(
            String queryText,
            Set<PageType> pageTypes,
            Instant since,
            int k) {
        String textHash = EmbeddingService.sha256(queryText);

        EmbeddingRequest embeddingRequest = new EmbeddingRequest(
                "",
                "query", // pageId for query context
                queryText,
                "text-embedding-3-large", // default model
                "v1", // schema version
                textHash);

        // Get query embedding (blocking call)
        EmbeddingResult embeddingResult = embeddingService.embedOne(embeddingRequest).block();
        if (embeddingResult == null || embeddingResult.vector() == null) {
            return List.of();
        }

        float[] queryVector = embeddingResult.vector();

        // Perform KNN search
        List<MemoryPageRepository.SearchResult> searchResults = pageRepo.knnSearch(
                queryVector,
                k,
                Optional.empty(), // no namespace filter
                Optional.empty() // no correlationId filter
        );

        // Convert SearchResult to VectorCandidate
        // Note: knnSearch returns distance (1 - similarity), so convert back to
        // similarity
        return searchResults.stream()
                .map(result -> new VectorCandidate(
                        result.page(),
                        1.0 - result.score() // Convert distance to similarity
                ))
                .toList();
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
        // Use updatedAt from the record
        Instant lastFactTime = page.updatedAt();
        if (lastFactTime != null &&
                Duration.between(lastFactTime, incoming.observedAt())
                        .compareTo(inactivityTimeout) > 0) {
            return true;
        }

        // Explicit boundary predicates
        // Check if the fact sentence contains completion indicators
        String sentence = incoming.sentence();
        if (sentence != null && (sentence.toLowerCase().contains("completed") ||
                sentence.toLowerCase().contains("dead_lettered"))) {
            return true;
        }

        return false;
    }

    private void closePage(MemoryPage page) {
        // Create a new page with closedAt timestamp and finalized summary
        String finalSummary = summarize(page);

        MemoryPage closedPage = new MemoryPage(
                page.pageId(),
                page.namespace(),
                page.correlationId(),
                page.pageType(),
                finalSummary,
                page.severityMax(),
                page.timestamp(),
                page.importance(),
                page.confidence(),
                page.createdAt(),
                Instant.now(), // updatedAt set to now when closing
                page.tags(),
                page.scope(),
                page.rawRef(),
                page.embedding());

        pageRepo.upsert(closedPage, Duration.ofDays(30));
    }

    private String renderFact(Fact fact) {
        return String.format("%s (at %s)",
                fact.sentence(),
                fact.observedAt());
    }
}
