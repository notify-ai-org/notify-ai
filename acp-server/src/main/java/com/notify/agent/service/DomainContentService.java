package com.notify.agent.service;

import com.notify.agent.DomainContentRepository;
import com.notify.agent.models.DomainContentEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads domain content (vocabulary, rules) for a client and aggregates
 * into a single JSON for AgentContext.domainContentJson.
 */
@Service
public class DomainContentService {

    private final DomainContentRepository repository;

    public DomainContentService(DomainContentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String loadByClientId(String clientId) {
        List<DomainContentEntity> list = repository.findByClientId(clientId);
        if (list == null || list.isEmpty())
            return null;

        Map<String, Object> aggregated = new HashMap<>();
        for (DomainContentEntity e : list) {
            if (e.getContentJson() != null && !e.getContentJson().isBlank()) {
                aggregated.put(e.getType().name().toLowerCase(), e.getContentJson());
            }
        }
        if (aggregated.isEmpty())
            return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(aggregated);
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Optional<DomainContentEntity> findByClientIdAndType(String clientId, DomainContentEntity.Type type) {
        return repository.findByClientIdAndType(clientId, type);
    }

    @Transactional
    public DomainContentEntity upsert(String clientId, DomainContentEntity.Type type, String contentJson,
            String version) {
        DomainContentEntity e = repository.findByClientIdAndType(clientId, type)
                .orElseGet(() -> {
                    DomainContentEntity n = new DomainContentEntity();
                    n.setClientId(clientId);
                    n.setType(type);
                    return n;
                });
        e.setContentJson(contentJson);
        if (version != null)
            e.setVersion(version);
        return repository.save(e);
    }
}
