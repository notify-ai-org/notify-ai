package com.example.agent.util;

import com.example.agent.AgentLogRepository;
import com.example.agent.AgentSnapshotRepository;
import com.example.agent.enums.AgentStage;
import com.example.agent.models.AgentStageChangeEvent;
import com.example.agent.service.SessionService;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Async;

import com.example.agent.models.AgentSnapshot;
import com.example.agent.models.AgentLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.agent.config.ObjectMapperFactory;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;

/**
 * Wrapper class that manages an individual agent's lifecycle and state
 * logging and metrics
 */

public class AgentWrapper {
    private static final Logger logger = Logger.getLogger(AgentWrapper.class.getName());

    private final String agentId;
    private final BaseAgent agent;
    private final AtomicReference<AgentStage> currentStage;
    private final Map<String, Object> agentState;
    private final Subject<AgentStageChangeEvent> stageChangeSubject;
    private final Instant createdAt;
    private Instant lastActivityAt;
    private String currentTaskId;
    private Exception lastError;

    private final SessionService sessionService;

    private final AgentSnapshotRepository snapshotRepo;
    private final AgentLogRepository logRepo;
    private final ObjectMapper mapper = ObjectMapperFactory.create();
    private final StatefulRedisConnection<String, String> redisConnection;
    private final ReentrantLock lock = new ReentrantLock();

    public AgentWrapper(String agentId, BaseAgent agent, AgentSnapshotRepository snapshotRepo,
            AgentLogRepository logRepo, StatefulRedisConnection<String, String> redisConnection,
            SessionService sessionService) {
        this.agentId = agentId;
        this.agent = agent;
        this.currentStage = new AtomicReference<>(AgentStage.CREATED);
        this.agentState = new ConcurrentHashMap<>();
        this.stageChangeSubject = PublishSubject.create();
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.snapshotRepo = snapshotRepo;
        this.logRepo = logRepo;
        this.redisConnection = redisConnection;
        this.sessionService = sessionService;

        persistSnapshot();
    }

    /**
     * Transition to a new stage and emit event
     */
    public boolean transitionTo(AgentStage newStage, String reason, Map<String, Object> metadata) {
        lock.lock();
        try {
            AgentStage previousStage = currentStage.get();

            if (isValidTransition(previousStage, newStage)) {
                currentStage.set(newStage);
                lastActivityAt = Instant.now();

                AgentStageChangeEvent event = new AgentStageChangeEvent(
                        agentId,
                        agent.name(),
                        previousStage,
                        newStage,
                        reason,
                        metadata);

                stageChangeSubject.onNext(event);
                logger.info(String.format("Agent %s transitioned from %s to %s: %s",
                        agentId, previousStage, newStage, reason));

                persistLog(AgentLog.LogType.STAGE_CHANGE, previousStage, newStage, reason, metadata, null);
                persistSnapshot();
                return true;
            } else {
                logger.warning(String.format("Invalid transition from %s to %s for agent %s",
                        previousStage, newStage, agentId));
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute the agent with the given context
     */
    public Flowable<Event> execute(InvocationContext context, String taskId, Content prompt) {
        this.currentTaskId = taskId;

        if (!transitionTo(AgentStage.RUNNING, "Starting execution", Map.of("taskId", taskId))) {
            return Flowable
                    .error(new IllegalStateException("Cannot start execution from stage: " + currentStage.get()));
        }

        // --- Setup Runner and Session ---
        Runner runner = new Runner(this.agent, taskId, new InMemoryArtifactService(), sessionService);
        Session session = context.session();
        logger.info(() -> String.format("Initial session state: %s", session.state()));

        // Use the modified session object for the run
        return runner.runAsync(context.userId(), session.id(), prompt)
                .doOnNext(event -> {
                    lastActivityAt = Instant.now();
                    logger.fine(String.format("Agent %s emitted event: %s", agentId, event.toJson()));
                    persistSnapshot();
                })
                .doOnComplete(() -> {
                    transitionTo(AgentStage.COMPLETED, "Execution completed successfully", Map.of("taskId", taskId));
                })
                .doOnError(error -> {
                    this.lastError = (Exception) error;
                    transitionTo(AgentStage.FAILED, "Execution failed: " + error.getMessage(),
                            Map.of("taskId", taskId, "error", error.getMessage()));
                });
    }

    /**
     * Pause the agent
     */
    public boolean pause(String reason) {
        return transitionTo(AgentStage.PAUSED, reason, Map.of());
    }

    /**
     * Resume the agent from paused state
     */
    public boolean resume(String reason) {
        if (currentStage.get() == AgentStage.PAUSED) {
            return transitionTo(AgentStage.READY, reason, Map.of());
        }
        return false;
    }

    /**
     * Terminate the agent
     */
    public boolean terminate(String reason) {
        return transitionTo(AgentStage.TERMINATED, reason, Map.of());
    }

    /**
     * Reset agent to ready state
     */
    public boolean reset(String reason) {
        if (currentStage.get() == AgentStage.TERMINATED) {
            return false; // Cannot reset terminated agent
        }
        this.currentTaskId = null;
        this.lastError = null;
        return transitionTo(AgentStage.READY, reason, Map.of());
    }

    /**
     * Check if agent is available for new tasks
     */
    public boolean isAvailable() {
        AgentStage stage = currentStage.get();
        return stage == AgentStage.READY || stage == AgentStage.IDLE;
    }

    /**
     * Get observable for stage change events
     */
    public Subject<AgentStageChangeEvent> getStageChangeObservable() {
        return stageChangeSubject;
    }

    /**
     * Update agent state
     */
    public void updateState(String key, Object value) {
        lock.lock();
        try {
            agentState.put(key, value);
            lastActivityAt = Instant.now();
            persistSnapshot();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get agent state value
     */
    public Object getState(String key) {
        return agentState.get(key);
    }

    /**
     * Get all agent state
     */
    public Map<String, Object> getAgentState() {
        return new ConcurrentHashMap<>(agentState);
    }

    // Getters
    public String getAgentId() {
        return agentId;
    }

    public BaseAgent getAgent() {
        return agent;
    }

    public AgentStage getCurrentStage() {
        return currentStage.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public Exception getLastError() {
        return lastError;
    }

    /**
     * Validate if transition is allowed
     */
    private boolean isValidTransition(AgentStage from, AgentStage to) {
        switch (from) {
            case CREATED:
                return to == AgentStage.INITIALIZING || to == AgentStage.TERMINATED;
            case INITIALIZING:
                return to == AgentStage.READY || to == AgentStage.FAILED || to == AgentStage.TERMINATED;
            case READY:
            case IDLE:
                return to == AgentStage.RUNNING || to == AgentStage.PAUSED || to == AgentStage.TERMINATED;
            case RUNNING:
                return to == AgentStage.COMPLETED || to == AgentStage.FAILED || to == AgentStage.PAUSED
                        || to == AgentStage.TERMINATED;
            case PAUSED:
                return to == AgentStage.READY || to == AgentStage.RUNNING || to == AgentStage.TERMINATED;
            case COMPLETED:
                return to == AgentStage.READY || to == AgentStage.IDLE || to == AgentStage.TERMINATED;
            case FAILED:
                return to == AgentStage.READY || to == AgentStage.TERMINATED;
            case TERMINATED:
                return false; // Terminal state
            default:
                return false;
        }
    }

    @Async
    private void persistSnapshot() {
        try {
            String stateJson = mapper.writeValueAsString(agentState);
            AgentSnapshot snapshot = new AgentSnapshot(
                    agentId,
                    agent.name(),
                    currentStage.get(),
                    createdAt,
                    lastActivityAt,
                    currentTaskId,
                    stateJson);
            if (snapshotRepo != null) {
                snapshotRepo.save(snapshot);
            }

            if (redisConnection != null) {
                try {
                    RedisCommands<String, String> commands = redisConnection.sync();
                    Map<String, String> hash = new HashMap<>();
                    hash.put("agentId", agentId);
                    hash.put("agentName", agent.name());
                    hash.put("stage", currentStage.get().name());
                    hash.put("createdAt", createdAt.toString());
                    hash.put("lastActivityAt", lastActivityAt.toString());
                    if (currentTaskId != null) {
                        hash.put("currentTaskId", currentTaskId);
                    }
                    hash.put("state", stateJson);

                    commands.hset("agent:snapshot:" + agentId, hash);
                } catch (Exception e) {
                    logger.warning("Failed to persist agent snapshot to Redis: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to persist agent snapshot: " + e.getMessage());
        }
    }

    private void persistLog(AgentLog.LogType type, AgentStage prev, AgentStage curr, String reason,
            Map<String, Object> meta, String eventContent) {
        try {
            String metaJson = meta != null ? mapper.writeValueAsString(meta) : null;
            AgentLog log = AgentLog.builder()
                    .agentId(agentId)
                    .timestamp(Instant.now())
                    .type(type)
                    .previousStage(prev)
                    .currentStage(curr)
                    .reason(reason)
                    .metadata(metaJson)
                    .eventContent(eventContent)
                    .build();
            if (logRepo != null) {
                logRepo.save(log);
            }
        } catch (Exception e) {
            logger.warning("Failed to persist agent log: " + e.getMessage());
        }
    }
}
