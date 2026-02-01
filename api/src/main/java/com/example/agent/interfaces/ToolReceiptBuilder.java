package com.example.agent.interfaces;

import com.example.agent.records.ToolReceipt;

import java.util.Map;

public interface ToolReceiptBuilder {
    ToolReceipt fromRaw(String toolName, String correlationId, Map<String, Object> rawInput, Map<String, Object> rawOutput);
}
