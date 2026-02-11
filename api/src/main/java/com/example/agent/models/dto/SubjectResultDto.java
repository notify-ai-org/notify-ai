package com.example.agent.models.dto;

import com.example.agent.models.subject.Subject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the result of executing a subject supplier method.
 * Contains the list of subjects returned by the supplier.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResultDto {

    /** Event key this subject supplier was executed for */
    private String eventKey;

    /** List of subjects returned by the supplier */
    private List<Subject> subjects;

    /** Execution time in milliseconds */
    private long executionTimeMillis;

    /** Whether the execution was successful */
    private boolean success;

    /** Error message if execution failed */
    private String errorMessage;
}
