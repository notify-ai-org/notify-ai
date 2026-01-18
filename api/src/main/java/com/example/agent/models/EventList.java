package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;

@Entity
public class EventList {
    @Id
    private String id;

    private String name;              // "OrderLifecycle"

    private String description;

    @OneToMany
    private List<Event> events;       // ordered list

    // Constructors
    public EventList() {}

    public EventList(String id, String name, String description, List<Event> events) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.events = events;
    }

    // Getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Event> getEvents() { return events; }
    public void setEvents(List<Event> events) { this.events = events; }
}
