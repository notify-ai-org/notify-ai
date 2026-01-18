package com.example.agent.models;

public class JobMetadata {
    private String id;
    private String payload;
    private int attempts;
    private long availableAt; // epoch millis when eligible

    public JobMetadata() {
    }

    public JobMetadata(String id, String payload, int attempts, long availableAt) {
        this.id = id;
        this.payload = payload;
        this.attempts = attempts;
        this.availableAt = availableAt;
    }

    // getters/setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public long getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(long availableAt) {
        this.availableAt = availableAt;
    }
}