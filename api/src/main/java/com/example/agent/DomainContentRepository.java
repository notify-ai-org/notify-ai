package com.example.agent;

import com.example.agent.models.DomainContentEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomainContentRepository extends JpaRepository<DomainContentEntity, String> {

    List<DomainContentEntity> findByClientId(String clientId);

    Optional<DomainContentEntity> findByClientIdAndType(String clientId, DomainContentEntity.Type type);
}
