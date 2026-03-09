package com.example.agent.exceptions;

public class AgentApplicationException extends RuntimeException {

    public AgentApplicationException(String message) {
        super(message);
    }

    public AgentApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
