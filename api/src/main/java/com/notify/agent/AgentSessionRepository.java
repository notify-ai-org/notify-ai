package com.notify.agent;

import com.notify.agent.models.AgentSessionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, String> {

    Optional<AgentSessionEntity> findBySessionId(String sessionId);

    Optional<AgentSessionEntity> findBySessionIdAndClientId(String sessionId, String clientId);

    List<AgentSessionEntity> findByClientId(String clientId);

}
