package com.notify.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class ExecutionResult {
    private Boolean success;
    @Column(columnDefinition = "TEXT")
    private String returnValue; // serialized return value
}
