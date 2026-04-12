package com.notify.agent;

import com.notify.agent.enums.AgentStage;
import com.notify.agent.enums.AgentOrchestratorEventType;
import com.notify.agent.models.AgentContext;
import com.notify.agent.models.AgentOrchestratorEvent;
import com.notify.agent.models.AgentSnapshot;
import com.notify.agent.models.AgentStageChangeEvent;
import com.notify.agent.service.SessionService;
import com.notify.agent.util.AgentWrapper;
import com.notify.agent.util.CentralExecutorRegistry;
import com.notify.agent.util.CentralExecutorRegistry.ExecutorType;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.genai.types.Content;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import io.lettuce.core.api.StatefulRedisConnection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.notify.agent.annotations.ManagedConfiguration;

/**
 * Agent orchestrator that manages a pool of agents, maintains state machines,
 * and emits stage change events. Tasks are enqueued and dispatched by a
 * continuously running dispatcher thread based on agent availability.
 */
@Service
public class AgentOrchestrator {
    private static final Logger logger = Logger.getLogger(AgentOrchestrator.class.getName());

    // Agent pool management - grouped by agentType (functional ID)
    private final Map<String, List<AgentWrapper>> agentPool;
    private final Map<String, List<AgentWrapper>> availableAgents;
    private final Map<String, List<AgentWrapper>> busyAgents;
    private final AtomicInteger nextAgentId;

    // Configuration — corePoolSize is the protected floor (never evicted);
    // maxPoolSize is the burst ceiling. Both are configurable via application
    // properties.
    @Value("${agent.orchestrator.core-pool-size:10}")
    @ManagedConfiguration(key = "agent.orchestrator.core-pool-size")
    private int corePoolSize = 10;

    @Value("${agent.orchestrator.max-pool-size:20}")
    @ManagedConfiguration(key = "agent.orchestrator.max-pool-size")
    private int maxPoolSize = 20;

    @Value("${agent.orchestrator.idle-timeout-seconds:300}")
    @ManagedConfiguration(key = "agent.orchestrator.idle-timeout-seconds")
    private long idleTimeoutSeconds = 300;

    @Value("${agent.orchestrator.cleanup-interval-seconds:60}")
    @ManagedConfiguration(key = "agent.orchestrator.cleanup-interval-seconds")
    private long cleanupIntervalSeconds = 60;

    // Event emission
    private final Subject<AgentStageChangeEvent> globalStageChangeSubject;
    private final Subject<AgentOrchestratorEvent> orchestratorEventSubject;

    // Overflow agent tracking (agents registered beyond corePoolSize)
    private final java.util.Set<String> overflowAgentIds;

    // Handle to the cleanup task — volatile so shutdown() sees the assignment made
    // by @PostConstruct
    private volatile ScheduledFuture<?> cleanupFuture;

    private final AgentSnapshotRepository snapshotRepo;
    private final AgentLogRepository logRepo;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final SessionService sessionService;
    private final CentralExecutorRegistry executorRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentOrchestrator(AgentSnapshotRepository snapshotRepo, AgentLogRepository logRepo,
            SessionService sessionService, CentralExecutorRegistry executorRegistry,
            StatefulRedisConnection<String, String> redisConnection) {
        this.snapshotRepo = snapshotRepo;
        this.logRepo = logRepo;
        this.sessionService = sessionService;
        this.executorRegistry = executorRegistry;
        this.agentPool = new ConcurrentHashMap<>();
        this.availableAgents = new ConcurrentHashMap<>();
        this.busyAgents = new ConcurrentHashMap<>();
        this.nextAgentId = new AtomicInteger(1);
        this.redisConnection = redisConnection;
        this.overflowAgentIds = ConcurrentHashMap.newKeySet();

        this.globalStageChangeSubject = PublishSubject.create();
        this.orchestratorEventSubject = PublishSubject.create();

        setupEventForwarding();
    }

    // -----------------------------------------------------------------------
    // Agent Registration
    // -----------------------------------------------------------------------

    /**
     * Register a new agent in the pool
     */
    public String registerAgent(String agentType, BaseAgent agent) {
        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();
        if (currentTotalSize >= maxPoolSize) {
            throw new IllegalStateException("Agent pool is full. Max size: " + maxPoolSize);
        }

        String agentId = "agent_" + nextAgentId.getAndIncrement();
        AgentWrapper wrapper = new AgentWrapper(agentId, agentType, agent, snapshotRepo, logRepo, sessionService);

        agentPool.computeIfAbsent(agentType, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(wrapper);
        availableAgents
                .computeIfAbsent(agentType, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(wrapper);

        // Track overflow agents (those registered beyond the protected core pool)
        if (currentTotalSize + 1 > corePoolSize) {
            overflowAgentIds.add(agentId);
        }

        // Subscribe to agent's stage changes
        wrapper.getStageChangeObservable().subscribe(globalStageChangeSubject::onNext);

        // Initialize agent
        wrapper.transitionTo(AgentStage.INITIALIZING, "Agent registered", Map.of("agentName", agent.name()));
        wrapper.transitionTo(AgentStage.READY, "Agent initialized", Map.of());

        logger.info(String.format("Registered agent %s (%s) of type %s in pool. Total pool size: %d",
                agentId, agent.name(), agentType, currentTotalSize + 1));

        emitOrchestratorEvent(AgentOrchestratorEventType.AGENT_REGISTERED,
                Map.of("agentId", agentId, "agentName", agent.name()));

        return agentId;
    }
    // -----------------------------------------------------------------------
    // Task Enqueue API (Reactive)
    // -----------------------------------------------------------------------

    /**
     * Build an execution Flowable for a given task and agent type.
     * The task is deferred until subscription occurs.
     */
    public Flowable<Event> createTaskFlowable(String agentType, String taskId, Content prompt, AgentContext context) {
        return Flowable.defer(() -> {
            AgentWrapper agent = waitForAvailableAgent(agentType);
            if (agent == null) {
                return Flowable.error(new IllegalStateException("No available agent of type " + agentType));
            }

            String activeAgentType = agent.getAgentType();
            availableAgents.get(activeAgentType).remove(agent);
            busyAgents.computeIfAbsent(activeAgentType,
                    k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                    .add(agent);

            logger.info(String.format("Dispatching task %s to agent %s (type: %s).",
                    taskId != null ? taskId : "direct", agent.getAgentId(), activeAgentType));

            emitOrchestratorEvent(AgentOrchestratorEventType.TASK_STARTED,
                    Map.of("taskId", taskId != null ? taskId : "direct", "agentId", agent.getAgentId()));

            BaseArtifactService artifactService = new InMemoryArtifactService();
            InvocationContext invocationContext = InvocationContext.create(
                    sessionService, artifactService, agent.getAgent(),
                    context != null ? context.getSession() : null, null, null);

            return agent.execute(invocationContext, taskId, prompt)
                    .doOnSubscribe(s -> {
                        if (context != null) {
                            AgentContextHolder.setContext(context);
                        }
                    })
                    .doFinally(() -> {
                        busyAgents.get(activeAgentType).remove(agent);
                        availableAgents.get(activeAgentType).add(agent);
                        agent.transitionTo(AgentStage.READY, "Task finished",
                                Map.of("taskId", taskId != null ? taskId : "direct"));
                        emitOrchestratorEvent(AgentOrchestratorEventType.TASK_COMPLETED,
                                Map.of("taskId", taskId != null ? taskId : "direct", "agentId", agent.getAgentId()));
                        AgentContextHolder.clear();
                    });
        }).subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io()); // Ensure acquiring blocks don't hold up caller
                                                                         // threads
    }

    /**
     * Composes a list of Flowables into a sequential execution stream.
     * Each task will only start after the previous one completes.
     */
    public Flowable<Event> createSequentialFlowable(List<Flowable<Event>> flowables) {
        if (flowables == null || flowables.isEmpty()) {
            return Flowable.empty();
        }
        return Flowable.concat(flowables);
    }

    /**
     * Composes a list of Flowables into a parallel execution stream.
     * All tasks run concurrently.
     */
    public Flowable<Event> createParallelFlowable(List<Flowable<Event>> flowables) {
        if (flowables == null || flowables.isEmpty()) {
            return Flowable.empty();
        }
        return Flowable.merge(flowables);
    }

    // -----------------------------------------------------------------------
    // Internal Agent Synchronization
    // -----------------------------------------------------------------------

    /**
     * Spin-wait for an available agent matching the requested type.
     * Returns null if the thread is interrupted while waiting.
     */
    public AgentWrapper waitForAvailableAgent(String agentType) {
        while (!Thread.currentThread().isInterrupted()) {
            AgentWrapper agent = (agentType == null) ? getAvailableAgent() : getAvailableAgent(agentType);
            if (agent != null) {
                return agent;
            }
            try {
                // Brief sleep to avoid tight spinning
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Agent Management
    // -----------------------------------------------------------------------

    /**
     * Pause a specific agent instance
     */
    public boolean pauseAgent(String agentId, String reason) {
        AgentWrapper agent = findAgentWrapper(agentId);
        if (agent != null) {
            boolean success = agent.pause(reason);
            if (success) {
                removeFromList(availableAgents, agent);
                removeFromList(busyAgents, agent);
            }
            return success;
        }
        return false;
    }

    /**
     * Resume a paused agent instance
     */
    public boolean resumeAgent(String agentId, String reason) {
        AgentWrapper agent = findAgentWrapper(agentId);
        if (agent != null) {
            boolean success = agent.resume(reason);
            if (success) {
                availableAgents.computeIfAbsent(agent.getAgentType(),
                        k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(agent);
            }
            return success;
        }
        return false;
    }

    /**
     * Get all agents and their states
     */
    public Map<String, Map<String, Object>> getAllAgentStates() {
        return agentPool.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(
                        AgentWrapper::getAgentId,
                        wrapper -> {
                            Map<String, Object> state = new HashMap<>(wrapper.getAgentState());
                            state.put("agentId", wrapper.getAgentId());
                            state.put("agentType", wrapper.getAgentType());
                            state.put("agentName", wrapper.getAgent().name());
                            state.put("currentStage", wrapper.getCurrentStage());
                            state.put("createdAt", wrapper.getCreatedAt());
                            state.put("lastActivityAt", wrapper.getLastActivityAt());
                            state.put("currentTaskId", wrapper.getCurrentTaskId());
                            return state;
                        }));
    }

    /**
     * Re-create agents from persisted snapshots stored in the database.
     */
    public int restoreFromSnapshots(Map<String, BaseAgent> agentsByName) {
        if (snapshotRepo == null) {
            logger.warning("restoreFromSnapshots: snapshotRepo is null, skipping restoration");
            return 0;
        }

        List<AgentStage> activeStages = List.of(
                AgentStage.RUNNING, AgentStage.PAUSED, AgentStage.FAILED);

        Set<AgentSnapshot> candidates = new LinkedHashSet<>();
        candidates.addAll(snapshotRepo.findByCurrentStageIn(activeStages));
        candidates.addAll(snapshotRepo.findByCurrentTaskIdIsNotNull());

        Set<String> alreadyRegisteredNames = agentPool.values().stream()
                .flatMap(List::stream)
                .map(w -> w.getAgent().name())
                .collect(Collectors.toSet());

        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();

        int restoredCount = 0;
        for (AgentSnapshot snapshot : candidates) {
            String agentName = snapshot.getAgentName();
            String agentType = snapshot.getAgentType();

            if (alreadyRegisteredNames.contains(agentName)) {
                logger.info(String.format("restoreFromSnapshots: skipping '%s' — already in pool", agentName));
                continue;
            }

            BaseAgent prototype = agentsByName.get(agentName);
            if (prototype == null) {
                logger.warning(String.format(
                        "restoreFromSnapshots: no prototype found for agent name '%s', skipping", agentName));
                continue;
            }

            if (currentTotalSize >= maxPoolSize) {
                logger.warning(String
                        .format("restoreFromSnapshots: pool full (%d/%d), cannot restore '%s'",
                                currentTotalSize, maxPoolSize, agentName));
                break;
            }

            try {
                String newAgentId = registerAgent(agentType != null ? agentType : agentName, prototype);
                restoredCount++;
                currentTotalSize++;
                logger.info(String.format(
                        "restoreFromSnapshots: restored agent '%s' as pool id '%s' (was %s, taskId=%s)",
                        agentName, newAgentId, snapshot.getCurrentStage(), snapshot.getCurrentTaskId()));

                // If the snapshot recorded a pending task, re-submit it
                String pendingTaskId = snapshot.getCurrentTaskId();
                if (pendingTaskId != null && !pendingTaskId.isBlank()) {
                    AgentWrapper wrapper = findAgentWrapper(newAgentId);
                    if (wrapper != null) {
                        // Build a minimal prompt indicating this is a task replay
                        // Content replayPrompt = com.google.genai.types.Content.fromParts(
                        // com.google.genai.types.Part.fromText(
                        // "[REPLAY] Resume task: " + pendingTaskId));
                        // executeTaskWithAgent(wrapper.getAgentType(), null, pendingTaskId,
                        // replayPrompt)
                        // .subscribe(
                        // event -> logger.fine("Replay event: " + event.toJson()),
                        // error -> logger.warning(String.format(
                        // "restoreFromSnapshots: replay of task '%s' for agent '%s' failed: %s",
                        // pendingTaskId, agentName, error.getMessage())));
                    }
                }
            } catch (Exception e) {
                logger.warning(String.format(
                        "restoreFromSnapshots: failed to restore agent '%s': %s", agentName, e.getMessage()));
            }
        }

        logger.info(String.format("restoreFromSnapshots: restored %d agent(s) from %d snapshot(s)",
                restoredCount, candidates.size()));
        return restoredCount;
    }

    /**
     * Get observable for all stage change events
     */
    public Subject<AgentStageChangeEvent> getStageChangeObservable() {
        return globalStageChangeSubject;
    }

    /**
     * Get observable for orchestrator events
     */
    public Subject<AgentOrchestratorEvent> getOrchestratorEventObservable() {
        return orchestratorEventSubject;
    }

    /**
     * Start the dispatcher thread and idle-agent cleanup scheduler.
     */
    @PostConstruct
    public void init() {
        // Start the idle-agent cleanup scheduler
        ScheduledExecutorService scheduler = (ScheduledExecutorService) executorRegistry.get(ExecutorType.SCHEDULER);
        cleanupFuture = scheduler.scheduleAtFixedRate(
                this::cleanupIdleOverflowAgents,
                cleanupIntervalSeconds, cleanupIntervalSeconds, TimeUnit.SECONDS);
        logger.info(String.format(
                "AgentOrchestrator initialized: reactive streaming enabled, cleanup interval=%ds, idleTimeout=%ds, corePool=%d, maxPool=%d",
                cleanupIntervalSeconds, idleTimeoutSeconds, corePoolSize, maxPoolSize));
    }

    /**
     * Evict overflow agents (those beyond corePoolSize) that have been idle
     * longer than idleTimeoutSeconds.
     */
    void cleanupIdleOverflowAgents() {
        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();
        if (currentTotalSize <= corePoolSize) {
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(idleTimeoutSeconds);

        List<AgentWrapper> toEvict = agentPool.values().stream()
                .flatMap(List::stream)
                .filter(w -> overflowAgentIds.contains(w.getAgentId()))
                .filter(w -> w.isAvailable() && w.getLastActivityAt().isBefore(cutoff))
                .collect(Collectors.toList());

        for (AgentWrapper wrapper : toEvict) {
            String agentId = wrapper.getAgentId();

            removeFromList(agentPool, wrapper);
            removeFromList(availableAgents, wrapper);
            removeFromList(busyAgents, wrapper);
            overflowAgentIds.remove(agentId);

            wrapper.terminate("idle-cleanup");
            logger.info(String.format("Evicted idle overflow agent %s (idle > %ds)",
                    agentId, idleTimeoutSeconds));
            emitOrchestratorEvent(AgentOrchestratorEventType.AGENT_EVICTED,
                    Map.of("agentId", agentId, "reason", "idle-timeout",
                            "idleTimeoutSeconds", idleTimeoutSeconds));
        }
    }

    /**
     * Shutdown the orchestrator
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down agent orchestrator...");

        // Cancel the cleanup scheduler
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
        }

        // Complete subjects
        globalStageChangeSubject.onComplete();
        orchestratorEventSubject.onComplete();

        logger.info("Agent orchestrator shutdown complete");
    }

    public AgentWrapper getAvailableAgent() {
        return availableAgents.values().stream()
                .flatMap(List::stream)
                .filter(AgentWrapper::isAvailable)
                .findFirst()
                .orElse(null);
    }

    private AgentWrapper getAvailableAgent(String agentType) {
        List<AgentWrapper> agents = availableAgents.get(agentType);
        if (agents == null || agents.isEmpty()) {
            return null;
        }
        synchronized (agents) {
            return agents.stream()
                    .filter(AgentWrapper::isAvailable)
                    .findFirst()
                    .orElse(null);
        }
    }

    private AgentWrapper findAgentWrapper(String agentId) {
        return agentPool.values().stream()
                .flatMap(List::stream)
                .filter(w -> w.getAgentId().equals(agentId))
                .findFirst()
                .orElse(null);
    }

    private void removeFromList(Map<String, List<AgentWrapper>> map, AgentWrapper wrapper) {
        List<AgentWrapper> list = map.get(wrapper.getAgentType());
        if (list != null) {
            list.remove(wrapper);
            if (list.isEmpty()) {
                map.remove(wrapper.getAgentType());
            }
        }
    }

    private void setupEventForwarding() {
        globalStageChangeSubject.subscribe(event -> {
            if (event.getCurrentStage() == AgentStage.TERMINATED) {
                emitOrchestratorEvent(AgentOrchestratorEventType.AGENT_STAGE_CHANGED,
                        Map.of("agentId", event.getAgentId(),
                                "stage", event.getCurrentStage()));
            }
        });
    }

    private void emitOrchestratorEvent(AgentOrchestratorEventType type, Map<String, Object> data) {
        AgentOrchestratorEvent event = new AgentOrchestratorEvent(type, data, Instant.now());
        orchestratorEventSubject.onNext(event);
    }

}
