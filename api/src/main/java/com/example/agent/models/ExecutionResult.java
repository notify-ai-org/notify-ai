package com.example.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class ExecutionResult {
    private boolean success;
    @Column(length = 5000)
    private String returnValue; // serialized return value
}
