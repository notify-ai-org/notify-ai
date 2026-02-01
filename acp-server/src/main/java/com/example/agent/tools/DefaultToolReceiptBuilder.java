package com.example.agent.tools;

import com.example.agent.interfaces.ToolReceiptBuilder;
import com.example.agent.records.ToolReceipt;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultToolReceiptBuilder implements ToolReceiptBuilder {
    @Override
    public ToolReceipt fromRaw(String toolName, String correlationId, Map<String, Object> rawInput, Map<String, Object> rawOutput) {
        Map<String, Object> key = new LinkedHashMap<>();

        // Keep only fields affecting decision
        keepIfPresent(key, rawInput, "to");
        keepIfPresent(key, rawInput, "templateId");
        keepIfPresent(key, rawInput, "channel");

        keepIfPresent(key, rawOutput, "status");
        keepIfPresent(key, rawOutput, "errorCode");
        keepIfPresent(key, rawOutput, "retryAfterSec");
        keepIfPresent(key, rawOutput, "providerMessageId");

        String status = String.valueOf(rawOutput.getOrDefault("status", "UNKNOWN"));
        String rawRef = String.valueOf(rawOutput.getOrDefault("rawRef", ""));

        return new ToolReceipt(toolName, correlationId, status, key, rawRef);
    }

    private void keepIfPresent(Map<String, Object> into, Map<String, Object> from, String k) {
        if (from != null && from.containsKey(k)) into.put(k, from.get(k));
    }
}
