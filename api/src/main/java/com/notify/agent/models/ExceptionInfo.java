package com.notify.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class ExceptionInfo {
    private String exceptionType; // java.lang.NullPointerException
    @Column(columnDefinition = "TEXT")
    private String message; // exception message
    @Column(columnDefinition = "TEXT")
    private String stackTrace; // full stack trace as string
}
