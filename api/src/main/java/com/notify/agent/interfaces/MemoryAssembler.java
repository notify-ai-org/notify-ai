package com.notify.agent.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.notify.agent.enums.PageType;
import com.notify.agent.records.Fact;
import com.notify.agent.records.MemoryPage;
import com.notify.agent.records.VectorCandidate;

public interface MemoryAssembler {

    /**
     * Incrementally update page summary with a new fact.
     * Should be cheap or no-op.
     */
    String incrementalUpdate(MemoryPage page, Fact newFact);

    /**
     * Build a final narrative summary when page closes.
     */
    String summarize(MemoryPage page);

    List<MemoryPage> buildPages(List<Fact> newFacts);

    MemoryPage findOrCreatePage(String namespace, Fact fact);

    List<VectorCandidate> search(
            String queryText,
            Set<PageType> pageTypes,
            Instant since,
            int k);
}
