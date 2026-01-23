package com.example.agent.service;

import com.example.agent.AgentSessionRepository;
import com.example.agent.models.AgentSessionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final AgentSessionRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionService(AgentSessionRepository repository) {
        this.repository = repository;
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
                e.setHistoryJson("[]");
                e.setCreatedAt(Instant.now());
                e.setUpdatedAt(Instant.now());
                return repository.save(e);
            });
    }

    @Transactional
    public AgentSessionEntity save(AgentSessionEntity session) {
        return repository.save(session);
    }

    /**
     * Append a history entry (JSON object) to the session's historyJson array.
     * historyJson is expected to be a JSON array string.
     */
    @Transactional
    public void appendHistory(String sessionId, String clientId, Object historyEntry) {
        repository.findBySessionIdAndClientId(sessionId, clientId).ifPresent(e -> {
            try {
                List<Object> list = new ArrayList<>();
                String prev = e.getHistoryJson();
                if (prev != null && !prev.isBlank()) {
                    list = mapper.readValue(prev, new TypeReference<List<Object>>() {});
                }
                list.add(historyEntry);
                e.setHistoryJson(mapper.writeValueAsString(list));
                e.setUpdatedAt(Instant.now());
                repository.save(e);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to append session history", ex);
            }
        });
    }

    @Override
    public Single<Session> createSession(String appName, String userId, ConcurrentMap<String, Object> state, String sessionId) {
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
                    e.setHistoryJson("[]");
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    // Save initial state in history if nonempty
                    if (state != null && !state.isEmpty()) {
                        try {
                            List<Object> historyList = new ArrayList<>();
                            historyList.add(Map.of("state", state));
                            e.setHistoryJson(mapper.writeValueAsString(historyList));
                        } catch (Exception ex) {
                            // ignore, just use as empty array
                        }
                    }
                    return repository.save(e);
                });
                 // Populate fields for Session interface
                return sessionEntity.toSession();
            }
        });
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId, String sessionId, Optional<GetSessionConfig> config) {
        Optional<AgentSessionEntity> sessionOpt = repository.findBySessionIdAndClientId(sessionId, appName)
                .filter(e -> userId == null || userId.isBlank() || userId.equals(e.getUserId()));
        if (sessionOpt.isPresent()) {
            try {
                return Maybe.just(sessionOpt.get().toSession());
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        } else {
            return Maybe.empty();
        }
        return null;
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return Single.fromCallable(() -> {
            List<AgentSessionEntity> entities = repository.findByClientId(appName, userId);
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
