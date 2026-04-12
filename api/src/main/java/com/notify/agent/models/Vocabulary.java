package com.notify.agent.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vocab_terms")
@Data
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

    @Transient
    private Object currentValue;

    @Transient
    private List<Vocabulary> children = new ArrayList<>();

}
