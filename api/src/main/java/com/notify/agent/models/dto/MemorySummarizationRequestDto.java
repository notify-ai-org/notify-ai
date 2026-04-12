package com.notify.agent.models.dto;

/**
 * DTO for MemorySummarizer agent input.
 */
public class MemorySummarizationRequestDto {
    private String facts;

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }
}
