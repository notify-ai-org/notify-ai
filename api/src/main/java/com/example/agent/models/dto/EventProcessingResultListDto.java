package com.example.agent.models.dto;

import java.util.List;

/**
 * Wrapper DTO for the EventProcessor agent output.
 * Wraps the array of EventProcessingResultDto in an object so the Google ADK
 * can store it in session state (which requires a JSON object, not a bare
 * array).
 */
public class EventProcessingResultListDto {

    private String result;
    private List<EventProcessingResultDto> items;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<EventProcessingResultDto> getItems() {
        return items;
    }

    public void setItems(List<EventProcessingResultDto> items) {
        this.items = items;
    }
}
