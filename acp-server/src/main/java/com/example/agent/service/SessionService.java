package com.example.agent.service;

import com.example.agent.AgentSessionRepository;
import com.example.agent.SessionEventRepository;
import com.example.agent.models.AgentSessionEntity;
import com.example.agent.models.SessionEventEntity;
import com.example.agent.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores and retrieves agent sessions from the database. Used by the
 * AuthenticationFilter to load session history and by agents to persist
 * updated history after runs.
 */
@Service
public class SessionService implements BaseSessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final AgentSessionRepository repository;
    private final SessionEventRepository sessionEventRepository;
    private final ObjectMapper mapper = ObjectMapperFactory.create();

    public SessionService(AgentSessionRepository repository, SessionEventRepository sessionEventRepository) {
        this.repository = repository;
        this.sessionEventRepository = sessionEventRepository;
    }

    /**
     * Persists a single ADK Event as a structured {@link SessionEventEntity} row
     * linked to the given session.
     * Safe to call after each agent turn to ensure query-able event history.
     */
    @Transactional
    public void appendEvent(String sessionId, Event event) {
        Optional<AgentSessionEntity> sessionOpt = repository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("appendEvent: session '{}' not found — skipping persistence", sessionId);
            return;
        }

        AgentSessionEntity session = sessionOpt.get();

        SessionEventEntity entity = new SessionEventEntity();
        entity.setSession(session);
        entity.setInvocationId(event.invocationId());
        entity.setAuthor(event.author());
        entity.setOccurredAt(Instant.now());

        // Extract role and first text part from event content
        event.content().ifPresent(content -> {
            // role from Content
            content.role().ifPresent(entity::setRole);

            // First text part as preview
            content.parts().ifPresent(parts -> {
                if (!parts.isEmpty()) {
                    parts.get(0).text().ifPresent(text -> {
                        // Truncate to 2000 chars for storage efficiency
                        entity.setTextContent(text.length() > 2000 ? text.substring(0, 2000) : text);
                    });
                }
            });
        });

        // Store raw JSON for full replay capability
        try {
            entity.setRawJson(mapper.writeValueAsString(event.toJson()));
        } catch (Exception e) {
            log.warn("appendEvent: failed to serialise raw event for session '{}': {}", sessionId, e.getMessage());
        }

        sessionEventRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<AgentSessionEntity> findBySessionId(String sessionId) {
        return repository.findBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public Optional<AgentSessionEntity> findBySessionIdAndClientId(String sessionId, String clientId) {
        return repository.findBySessionIdAndClientId(sessionId, clientId);
    }

    /**
     * Create a new session or return existing for the given ids. When creating,
     * sessionId can be provided or generated.
     */
    @Transactional
    public AgentSessionEntity createOrGet(String sessionId, String clientId, String userId, String scope) {
        final String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? "sess_" + UUID.randomUUID().toString().replace("-", "")
                : sessionId;
        return repository.findBySessionIdAndClientId(effectiveSessionId, clientId)
                .orElseGet(() -> {
                    AgentSessionEntity e = new AgentSessionEntity();
                    e.setSessionId(sessionId);
                    e.setClientId(clientId);
                    e.setUserId(userId);
                    e.setScope(scope);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return repository.save(e);
                });
    }

    @Transactional
    public AgentSessionEntity save(AgentSessionEntity session) {
        return repository.save(session);
    }

    @SuppressWarnings("null")
    @Override
    public Single<Session> createSession(String appName, String userId, ConcurrentMap<String, Object> state,
            String sessionId) {
        final String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? "sess_" + UUID.randomUUID().toString().replace("-", "")
                : sessionId;
        return Single.fromCallable(new Callable<Session>() {
            @Override
            public Session call() throws Exception {
                AgentSessionEntity sessionEntity = repository.findBySessionIdAndClientId(effectiveSessionId, appName)
                        .orElseGet(() -> {
                            AgentSessionEntity e = new AgentSessionEntity();
                            e.setSessionId(sessionId);
                            e.setClientId(appName);
                            e.setUserId(userId);
                            e.setScope(""); // or some default scope, adjust as needed
                            e.setCreatedAt(Instant.now());
                            e.setUpdatedAt(Instant.now());
                            return repository.save(e);
                        });
                // Populate fields for Session interface
                return sessionEntity.toSession();
            }
        });
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId, String sessionId,
            Optional<GetSessionConfig> config) {
        Optional<AgentSessionEntity> sessionOpt = repository.findBySessionIdAndClientId(sessionId, appName)
                .filter(e -> userId == null || userId.isBlank() || userId.equals(e.getUserId()));
        if (sessionOpt.isEmpty()) {
            // Also try lookup by primary key (id) since toSession() uses the DB id
            sessionOpt = repository.findById(sessionId)
                    .filter(e -> userId == null || userId.isBlank() || userId.equals(e.getUserId()));
        }
        if (sessionOpt.isPresent()) {
            try {
                return Maybe.just(sessionOpt.get().toSession());
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        // Session not found — create it so the Runner never fails with "Session not
        // found"
        try {
            AgentSessionEntity created = createOrGet(sessionId, appName, userId, "");
            return Maybe.just(created.toSession());
        } catch (Exception e) {
            return Maybe.error(e);
        }
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return Single.fromCallable(() -> {
            List<AgentSessionEntity> entities = repository.findByClientId(userId);
            List<Session> sessionList = new ArrayList<>();
            for (AgentSessionEntity entity : entities) {
                sessionList.add(entity.toSession());
            }
            return ListSessionsResponse.builder()
                    .sessions(sessionList)
                    .build();
        });
    }

    @Override
    public Completable deleteSession(String appName, String userId, String sessionId) {
        return Completable.fromAction(() -> {
            repository.findBySessionIdAndClientId(sessionId, appName)
                    .filter(e -> userId == null || userId.isBlank() || userId.equals(e.getUserId()))
                    .ifPresent(repository::delete);
        });
    }

    @Override
    public Single<ListEventsResponse> listEvents(String appName, String clientId, String sessionId) {
        return Single.fromCallable(() -> {
            // Assuming there is an event repository to retrieve events by session
            AgentSessionEntity sessionEntity = repository
                    .findBySessionIdAndClientId(sessionId, appName).orElseThrow();

            List<Event> events = sessionEntity.toSession().events();
            // Filter by userId if given
            if (clientId != null && !clientId.isBlank()) {
                events = events.stream()
                        .filter(e -> clientId.equals(e.author()))
                        .toList();
            }
            return ListEventsResponse.builder()
                    .events(events)
                    .build();
        });
    }
}
