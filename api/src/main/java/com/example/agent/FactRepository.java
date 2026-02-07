package com.example.agent;

import com.example.agent.models.FactEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactRepository extends JpaRepository<FactEntity, Long> {
    List<FactEntity> findByClientIdAndObservedAtAfter(String clientId, Instant after);
    List<FactEntity> findByCorrelationId(String correlationId);
}

