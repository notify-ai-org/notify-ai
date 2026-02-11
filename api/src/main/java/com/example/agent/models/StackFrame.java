package com.example.agent.models;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class StackFrame {
    private String className; // "com.example.OrderService"
    private String methodName; // "createOrder"
    private int lineNumber; // line number in code
    private String fileName; // source file
}
