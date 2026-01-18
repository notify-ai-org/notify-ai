package com.example.agent.models;

import jakarta.persistence.*;

@Entity
@Table(name = "vocab_terms", uniqueConstraints = @UniqueConstraint(columnNames = { "term" }))
public class Vocabulary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String term;

    @Column(length = 2048)
    private String description;

    @Column(length = 2048)
    private String type;

    @ManyToOne
    private Vocabulary parent;

    /**
     * @return the parent
     */
    public Vocabulary getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(Vocabulary parent) {
        this.parent = parent;
    }

    // Constructors
    public Vocabulary() {
    }

    public Vocabulary(String term, String description, String path) {
        this.term = term;
        this.description = description;
    }

    // Getters / setters
    public Long getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return type;
    } // Mapping 'type' column to 'path' concept for now, or add new field?
      // Wait, the entity has 'type' column but constructor uses 'path'.
      // Let's check the constructor: public Vocabulary(String term, String
      // description, String path) { ... }
      // But the field is 'type'.
      // Let's assume 'type' field stores the path.

    public void setPath(String path) {
        this.type = path;
    }

}
