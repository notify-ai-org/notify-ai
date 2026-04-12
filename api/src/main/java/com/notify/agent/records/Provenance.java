package com.notify.agent.records;

import java.util.List;
import java.util.Map;

public record Provenance(
        List<RetrievalQuery> queries,
        Map<String, String> selectionReasons,  // pageId -> reason
        List<String> droppedPageIds
) {
}
