package com.notify.agent.records;

import com.notify.agent.enums.PageType;

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
