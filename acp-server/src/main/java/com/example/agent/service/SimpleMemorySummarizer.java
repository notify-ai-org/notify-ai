package com.example.agent.util;

public class SimpleMemorySummarizer implements MemorySummarizer {

    private final Duration windowSize;
    private final Duration inactivityTimeout;
    private final int maxFactsPerPage;

    private final MemoryPageRepository pageRepo;
    private final MemorySummarizer summarizer;

    public SimpleMemorySummarizer(
            Duration windowSize,
            Duration inactivityTimeout,
            int maxFactsPerPage,
            MemoryPageRepository pageRepo
    ) {

        this.windowSize = windowSize;
        this.inactivityTimeout = inactivityTimeout;
        this.maxFactsPerPage = maxFactsPerPage;
        this.pageRepo = pageRepo;
        this.summarizer = summarizer;
    }

    @Override
    public String incrementalUpdate(MemoryPage page, Fact newFact) {
        if (page.summary == null) {
            return "- " + renderFact(newFact);
        }
        return page.summary + "\n- " + renderFact(newFact);
    }

    @Override
    public String summarize(MemoryPage page) {
        return page.summary; // or call LLM here
    }

    private String renderFact(Fact f) {
        return f.subject + " " + f.predicate + " " + f.object;
    }

    private String resolveNamespace(Fact fact) {
        if (fact.subject != null) {
            return fact.subject; // e.g. "notification:n123"
        }
        if (fact.correlationId != null) {
            return "correlation:" + fact.correlationId;
        }
        return "tenant:" + fact.tenantId;
    }

    public List<MemoryPage> buildPages(List<Fact> newFacts) {
        if (newFacts == null || newFacts.isEmpty()) {
            return List.of();
        }

        // 1. Group facts by namespace
        Map<String, List<Fact>> byNamespace =
                newFacts.stream()
                        .collect(Collectors.groupingBy(this::resolveNamespace));

        List<MemoryPage> updatedPages = new ArrayList<>();

        for (var entry : byNamespace.entrySet()) {
            String namespace = entry.getKey();
            List<Fact> facts = entry.getValue();

            // Sort facts by time (important for determinism)
            facts.sort(Comparator.comparing(f -> f.observedAt));

            for (Fact fact : facts) {
                MemoryPage page = findOrCreatePage(namespace, fact);
                appendFact(page, fact);
                updatedPages.add(page);
            }
        }

        return updatedPages;
    }


    private MemoryPage findOrCreatePage(String namespace, Fact fact) {

        Instant now = fact.observedAt;
        Instant windowStart = alignToWindow(now);

        // Try to find an open page
        Optional<MemoryPage> open =
                pageRepo.findOpenPage(namespace, windowStart);

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
        page.factIds = new ArrayList<>();
        page.createdAt = Instant.now();

        pageRepo.save(page);
        return page;
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

        if (page.factIds.size() >= maxFactsPerPage) {
            return true;
        }

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
        page.summary = summarizer.summarize(page);
        pageRepo.save(page);
    }


}
