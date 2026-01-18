package com.example.agent;

import com.example.agent.models.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {
    List<Vocabulary> findByTermIgnoreCaseIn(List<String> terms);

    java.util.Optional<Vocabulary> findByTermIgnoreCase(String term);
}
