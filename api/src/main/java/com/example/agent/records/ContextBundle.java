package com.example.agent.records;

import java.util.List;

public record ContextBundle(
                List<Fact> facts,
                List<MemoryPage> pages,
                List<ToolReceipt> toolReceipts,
                Provenance provenance,
                int tokenEstimate,
                List<String> sessionEventSummaries,
                String sessionHistorySummary) {

    /** Convenience constructor for callers that don't supply event history. */
    public ContextBundle(List<Fact> facts, List<MemoryPage> pages, List<ToolReceipt> toolReceipts,
                         Provenance provenance, int tokenEstimate) {
        this(facts, pages, toolReceipts, provenance, tokenEstimate, List.of(), null);
    }
}
