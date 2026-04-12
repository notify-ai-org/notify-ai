package com.notify.agent.interfaces;

import com.notify.agent.records.ToolReceipt;

import java.util.Map;

public interface ToolReceiptBuilder {
    ToolReceipt fromRaw(String toolName, String correlationId, Map<String, Object> rawInput, Map<String, Object> rawOutput);
}
