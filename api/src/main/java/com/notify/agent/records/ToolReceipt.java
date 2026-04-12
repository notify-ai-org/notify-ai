package com.notify.agent.records;

import java.util.Map;

public record ToolReceipt(
        String toolName,
        String correlationId,
        String status,
        Map<String, Object> keyFields,
        String rawRef
) {
}
