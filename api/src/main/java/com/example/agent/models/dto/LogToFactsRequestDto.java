package com.example.agent.models.dto;

import java.util.List;

/**
 * DTO for LogToFactsAgent input: who/what logs to convert to facts.
 */
public class LogToFactsRequestDto {

    private String clientId;
    private String sourceType;
    private List<String> rawLogs;
    private String correlationId;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public List<String> getRawLogs() { return rawLogs; }
    public void setRawLogs(List<String> rawLogs) { this.rawLogs = rawLogs; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}

