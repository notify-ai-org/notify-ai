package com.example.agent.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_templates")
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String subject;

    @Column(length = 2048, nullable = false)
    private String template;

    @Column(length = 512)
    private String eventType; // Optional: link to event type if available

    @Column(length = 512)
    private String eventName; // Optional: link to event type if available

    @Column(nullable = false)
    private Instant createdAt;

    public MessageTemplate() {
        this.createdAt = Instant.now();
    }

    public MessageTemplate(String channel, String subject, String template) {
        this();
        this.channel = channel;
        this.subject = subject;
        this.template = template;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * @return the eventName
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * @param eventName the eventName to set
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }
}

