package com.example.agent.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;

@Entity
public class EventGraph {
    @Id
    private String id;

    private String name;              // "Payment Workflow"

    private String correlationId;     // same grouping as EventList

    @OneToMany
    private List<EventGraphEdge> edges;

    // Constructors
    public EventGraph() {}

    public EventGraph(String id, String name, String correlationId, List<EventGraphEdge> edges) {
        this.id = id;
        this.name = name;
        this.correlationId = correlationId;
        this.edges = edges;
    }

    // Getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public List<EventGraphEdge> getEdges() { return edges; }
    public void setEdges(List<EventGraphEdge> edges) { this.edges = edges; }
}

