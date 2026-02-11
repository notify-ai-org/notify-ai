package com.example.agent.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class ExceptionInfo {
    private String exceptionType; // java.lang.NullPointerException
    @Column(length = 1000)
    private String message; // exception message
    @Column(length = 10000)
    private String stackTrace; // full stack trace as string
}
