package com.example.agent;

import com.example.agent.models.FactEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FactRepository extends JpaRepository<FactEntity, Long> {
    List<FactEntity> findByClientIdAndObservedAtAfter(String clientId, Instant after);

    List<FactEntity> findByObservedAtAfter(Instant after);

    List<FactEntity> findByCorrelationId(String correlationId);

    @Modifying
    @Query(value = """
                INSERT INTO fact (
                fact_id, tenant_id, subject, predicate, object,
                observed_at, severity, confidence, correlation_id, created_at
                )
                VALUES (
                :factId, :tenantId, :subject, :predicate, :object,
                :observedAt, :severity, :confidence, :correlationId, now()
                )
                ON CONFLICT (fact_id)
                DO UPDATE SET
                confidence = GREATEST(fact.confidence, EXCLUDED.confidence)
            """, nativeQuery = true)
    void upsertNative(
            String factId,
            String tenantId,
            String subject,
            String predicate,
            String object,
            Instant observedAt,
            String severity,
            double confidence,
            String correlationId);

}
