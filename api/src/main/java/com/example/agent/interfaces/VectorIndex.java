package com.example.agent.interfaces;

import com.example.agent.records.EntityRef;
import com.example.agent.enums.PageType;
import com.example.agent.records.VectorCandidate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface VectorIndex {
    List<VectorCandidate> search(
            String tenantId,
            String queryText,
            List<EntityRef> scope,
            Set<PageType> pageTypes,
            Instant since,
            int k
    );
}
