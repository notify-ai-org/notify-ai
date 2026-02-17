package com.example.agent.store;

import com.example.agent.FactRepository;
import com.example.agent.enums.DecisionType;
import com.example.agent.interfaces.FactStore;
import com.example.agent.models.FactEntity;
import com.example.agent.records.EntityRef;
import com.example.agent.records.Fact;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DefaultFactStore implements FactStore {

    private final FactRepository factRepository;
    private final ObjectMapper objectMapper;

    public DefaultFactStore(FactRepository factRepository, ObjectMapper objectMapper) {
        this.factRepository = factRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Fact> fetchFacts(String tenantId, List<EntityRef> scope, DecisionType decisionType, Instant now) {
        // Fetch facts for the tenant that were observed before 'now'
        // Using a lookback window - adjust as needed based on your requirements
        Instant lookbackStart = now.minusSeconds(86400 * 30); // Last 30 days
        List<FactEntity> entities = factRepository.findByClientIdAndObservedAtAfter(tenantId, lookbackStart);

        // Convert FactEntity to Fact record
        return entities.stream()
                .filter(entity -> entity.getObservedAt().isBefore(now) || entity.getObservedAt().equals(now))
                .map(this::toFact)
                .collect(Collectors.toList());
    }

    private Fact toFact(FactEntity entity) {
        List<String> sourceEventIds = parseSourceEventIds(entity.getSourceEventIdsJson());

        return new Fact(
                String.valueOf(entity.getId()), // factId - using the entity ID
                entity.getClientId(), // tenantId
                entity.getFactType(), // factType
                entity.getSentence(), // sentence
                entity.getObservedAt(), // observedAt
                entity.getConfidence(), // confidence
                entity.getImportance(), // importance
                entity.getTtlDays(), // ttlDays
                sourceEventIds, // sourceEventIds
                entity.getCorrelationId() // correlationId
        );
    }

    private List<String> parseSourceEventIds(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            // If JSON parsing fails, try treating it as CSV
            return List.of(json.split(","));
        }
    }
}
