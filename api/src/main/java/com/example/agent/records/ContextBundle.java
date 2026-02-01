package com.example.agent.records;

import java.util.List;

public record ContextBundle(
        List<Fact> facts,
        List<MemoryPage> pages,
        List<ToolReceipt> toolReceipts,
        Provenance provenance,
        int tokenEstimate
) {
}
