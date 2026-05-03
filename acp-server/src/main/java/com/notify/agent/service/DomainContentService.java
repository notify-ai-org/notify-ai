package com.notify.agent.service;

import com.notify.agent.DomainContentRepository;
import com.notify.agent.models.DomainContentEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DomainContentService {

    private final DomainContentRepository repository;

    public DomainContentService(DomainContentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> loadByClientId(String clientId) {
        List<DomainContentEntity> list = repository.findByClientId(clientId);
        if (list == null || list.isEmpty())
            return null;

        Map<String, String> aggregated = new HashMap<>();
        for (DomainContentEntity e : list) {
            if (e.getContent() != null && !e.getContent().isBlank()) {
                aggregated.put(e.getKeyName(), e.getContent());
            }
        }

        return aggregated;
    }

    @Transactional(readOnly = true)
    public List<DomainContentEntity> findByClientIdAndType(String clientId, DomainContentEntity.Type type) {
        return repository.findByClientIdAndType(clientId, type);
    }

    @Transactional
    public List<DomainContentEntity> upsert(String clientId, List<Map<String, String>> missingKeys) {
        List<DomainContentEntity> list = new ArrayList<>();
        for (Map<String, String> map : missingKeys) {
            String key = map.get("key");
            String description = map.get("description");
            String type = map.get("type");
            DomainContentEntity.Type t = DomainContentEntity.Type.valueOf(type);
            DomainContentEntity e = new DomainContentEntity();
            e.setClientId(clientId);
            e.setType(t);
            e.setKeyName(key);
            e.setDescription(description);
            e.setContent("");
            if (e.getVersion() != null)
                e.setVersion(e.getVersion() + 1);
            else
                e.setVersion(1);
            list.add(e);
        }
        return repository.saveAll(list);
    }
}
