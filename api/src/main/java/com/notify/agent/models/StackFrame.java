package com.notify.agent.models;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class StackFrame {
    private String className; // "com.notify.OrderService"
    private String methodName; // "createOrder"
    private int lineNumber; // line number in code
    private String fileName; // source file
}
