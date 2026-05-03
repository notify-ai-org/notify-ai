package com.notify.agent;

import com.notify.agent.models.DomainContentEntity;
import com.notify.agent.models.DomainContentEntity.Type;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomainContentRepository extends JpaRepository<DomainContentEntity, String> {

    List<DomainContentEntity> findByClientId(String clientId);

    List<DomainContentEntity> findByClientIdAndType(String clientId, DomainContentEntity.Type type);

    Optional<DomainContentEntity> findByClientIdAndTypeAndKeyName(String clientId, Type type, String key);
}
