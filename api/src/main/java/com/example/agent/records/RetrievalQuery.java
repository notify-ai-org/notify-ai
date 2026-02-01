package com.example.agent.records;

import com.example.agent.enums.PageType;

import java.util.List;
import java.util.Set;

public record RetrievalQuery(
        String queryText,
        Set<String> tags,
        List<EntityRef> scope,
        PageType pageType,
        int k
) {
}
