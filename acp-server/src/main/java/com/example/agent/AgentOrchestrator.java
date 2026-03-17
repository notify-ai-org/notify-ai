package com.example.agent;

import com.example.agent.enums.AgentStage;
import com.example.agent.models.AgentContext;
import com.example.agent.models.AgentSnapshot;
import com.example.agent.models.AgentStageChangeEvent;

import com.example.agent.service.SessionService;
import com.example.agent.util.AgentWrapper;
import com.example.agent.util.CentralExecutorRegistry;
import com.example.agent.util.CentralExecutorRegistry.ExecutorType;
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

import com.example.agent.annotations.ManagedConfiguration;

/**
 * Agent orchestrator that manages a pool of agents, maintains state machines,
 * and emits stage change events. Similar to a thread pool executor but for
 * agents.
 * State buckets
 * Prober
 * Batch job execution
 * Efficient job transfer
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

    /**
     * Register a new agent in the pool
     */
    public String registerAgent(String agentType, BaseAgent agent) {
        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();
        if (currentTotalSize >= maxPoolSize) {
            throw new IllegalStateException("Agent pool is full. Max size: " + maxPoolSize);
        }

        String agentId = "agent_" + nextAgentId.getAndIncrement();
        AgentWrapper wrapper = new AgentWrapper(agentId, agentType, agent, snapshotRepo, logRepo, redisConnection,
                sessionService);

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

    /**
     * Execute a task using any available agent from any type
     */
    public Flowable<Event> executeTask(InvocationContext context, String taskId, Content prompt) {
        AgentWrapper agent = getAvailableAgent();
        if (agent == null) {
            return Flowable.error(new IllegalStateException("No available agents in pool"));
        }

        String type = agent.getAgentType();
        String id = agent.getAgentId();

        // Move agent from available to busy
        availableAgents.get(type).remove(agent);
        busyAgents.computeIfAbsent(type, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(agent);

        logger.info(String.format("Executing task %s with agent %s of type %s", taskId, id, type));

        return agent.execute(context, taskId, prompt)
                .doFinally(() -> {
                    // Move agent back to available
                    busyAgents.get(type).remove(agent);
                    availableAgents.get(type).add(agent);
                    agent.transitionTo(AgentStage.READY, "Task finished", Map.of("taskId", taskId));
                });
    }

    /**
     * Execute a task with a specific agent
     */
    /**
     * Execute a task with a specific agent type.
     * Picks the first available instance of that type.
     */
    public Flowable<Event> executeTaskWithAgent(String agentType, InvocationContext context, String taskId,
            Content prompt) {
        AgentWrapper agent = getAvailableAgent(agentType);
        AgentContext agentContext = AgentContextHolder.getContext();
        BaseArtifactService artifactService = new InMemoryArtifactService();
        final String finalTaskId = (taskId == null) ? UUID.randomUUID().toString() : taskId;

        if (agent == null) {
            return Flowable.error(new IllegalStateException("No available instances for agent type: " + agentType));
        }

        if (context == null) {
            context = InvocationContext.create(sessionService, artifactService, agent.getAgent(),
                    agentContext.getSession(), null, null);
        }

        // Move agent from available to busy
        availableAgents.get(agentType).remove(agent);
        busyAgents.computeIfAbsent(agentType, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(agent);

        return agent.execute(context, finalTaskId, prompt)
                .doFinally(() -> {
                    busyAgents.get(agentType).remove(agent);
                    availableAgents.get(agentType).add(agent);
                    agent.transitionTo(AgentStage.READY, "Task finished", Map.of("taskId", finalTaskId));
                });
    }

    /**
     * Execute tasks with multiple agents sequentially.
     * Each agent will execute its task one after another, in the order provided.
     * 
     * @param agentContexts A list of AgentTaskContext containing agentId, context,
     *                      taskId, prompt for each agent call
     * @return Flowable<Event> that emits all events in sequence
     */
    public Flowable<Event> executeTasksSequentially(List<AgentTaskContext> agentContexts) {
        Flowable<Event> flowable = Flowable.empty();

        for (AgentTaskContext atc : agentContexts) {
            flowable = flowable.concatWith(
                    executeTaskWithAgent(atc.agentType, atc.context, atc.taskId, atc.prompt));
        }
        return flowable;
    }

    /**
     * Execute tasks with multiple agents in parallel.
     * All given agent tasks are started simultaneously.
     * 
     * @param agentContexts A list of AgentTaskContext containing agentId, context,
     *                      taskId, prompt for each agent call
     * @return Flowable<Event> that emits all events from all agents in parallel
     */
    public Flowable<Event> executeTasksInParallel(List<AgentTaskContext> agentContexts) {
        List<Flowable<Event>> flowables = new java.util.ArrayList<>();
        for (AgentTaskContext atc : agentContexts) {
            flowables.add(executeTaskWithAgent(atc.agentType, atc.context, atc.taskId, atc.prompt));
        }
        return Flowable.merge(flowables);
    }

    /**
     * Helper data class to encapsulate agent call context for sequential/parallel
     * execution.
     */
    public class AgentTaskContext {
        public final String agentType;
        public final InvocationContext context;
        public final String taskId;
        public final Content prompt;

        public AgentTaskContext(String agentType, InvocationContext context, String taskId, Content prompt) {
            this.agentType = agentType;
            this.context = context;
            this.taskId = taskId;
            this.prompt = prompt;
        }
    }

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
     *
     * <p>
     * Call this once at startup (after {@link AgentRegistry} has registered the
     * core agents)
     * to restore any agents that were active or paused when the process last
     * restarted, and
     * re-submit any tasks that were in-progress at the time of the snapshot.
     *
     * <p>
     * Rehydration logic:
     * <ol>
     * <li>Load all snapshots whose stage was RUNNING, PAUSED, or FAILED,
     * <em>or</em> that
     * carried a non-null {@code currentTaskId} (i.e. had an active task).</li>
     * <li>Skip snapshots for agents already registered in the pool (e.g. core
     * agents loaded
     * by {@code AgentRegistry} at startup).</li>
     * <li>For each remaining snapshot, look up the {@link BaseAgent} prototype by
     * name from
     * the {@code agentsByName} catalogue, register it as a new pool entry, and—if
     * the
     * snapshot had a pending task—re-queue that task immediately using the stored
     * {@code currentTaskId}.</li>
     * </ol>
     *
     * @param agentsByName catalogue of available agent prototypes keyed by
     *                     {@link BaseAgent#name()}, typically built from
     *                     {@link AgentRegistry}
     * @return number of agents successfully rehydrated
     */
    public int restoreFromSnapshots(Map<String, BaseAgent> agentsByName) {
        if (snapshotRepo == null) {
            logger.warning("restoreFromSnapshots: snapshotRepo is null, skipping restoration");
            return 0;
        }

        // Stages that indicate the agent was actively doing something at last shutdown
        List<AgentStage> activeStages = List.of(
                AgentStage.RUNNING, AgentStage.PAUSED, AgentStage.FAILED);

        // Collect candidates: active-stage snapshots plus any with a pending task id
        Set<AgentSnapshot> candidates = new LinkedHashSet<>();
        candidates.addAll(snapshotRepo.findByCurrentStageIn(activeStages));
        candidates.addAll(snapshotRepo.findByCurrentTaskIdIsNotNull());

        // Derive the set of agentNames already present in the pool to avoid duplicates
        Set<String> alreadyRegisteredNames = agentPool.values().stream()
                .flatMap(List::stream)
                .map(w -> w.getAgent().name())
                .collect(Collectors.toSet());

        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();

        int restoredCount = 0;
        for (AgentSnapshot snapshot : candidates) {
            String agentName = snapshot.getAgentName();
            String agentType = snapshot.getAgentType(); // Use the new agentType field

            // Skip if already in pool (e.g. core agent loaded at startup)
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
                // Register a fresh wrapper for this agent prototype
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
                        //         com.google.genai.types.Part.fromText(
                        //                 "[REPLAY] Resume task: " + pendingTaskId));
                        // executeTaskWithAgent(wrapper.getAgentType(), null, pendingTaskId, replayPrompt)
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
     * Start the idle-agent cleanup scheduler.
     * Called by Spring after all @Value fields have been injected.
     */
    @PostConstruct
    public void initCleanupScheduler() {
        ScheduledExecutorService scheduler = (ScheduledExecutorService) executorRegistry.get(ExecutorType.SCHEDULER);
        cleanupFuture = scheduler.scheduleAtFixedRate(
                this::cleanupIdleOverflowAgents,
                cleanupIntervalSeconds, cleanupIntervalSeconds, TimeUnit.SECONDS);
        logger.info(String.format(
                "Agent cleanup scheduler started: interval=%ds, idleTimeout=%ds, corePool=%d, maxPool=%d",
                cleanupIntervalSeconds, idleTimeoutSeconds, corePoolSize, maxPoolSize));
    }

    /**
     * Evict overflow agents (those beyond corePoolSize) that have been idle
     * longer than idleTimeoutSeconds. Package-private so unit tests can invoke it
     * directly.
     */
    void cleanupIdleOverflowAgents() {
        int currentTotalSize = agentPool.values().stream().mapToInt(List::size).sum();
        if (currentTotalSize <= corePoolSize) {
            return; // nothing to evict — pool is within the protected floor
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

        // StatefulRedisConnection lifecycle is managed by Spring; no manual close
        // needed
    }

    // Private helper methods

    private AgentWrapper getAvailableAgent() {
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
        // Forward agent stage changes to orchestrator events
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

    // Inner classes for orchestrator events

    public enum AgentOrchestratorEventType {
        AGENT_REGISTERED,
        AGENT_STAGE_CHANGED,
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_FAILED,
        POOL_FULL,
        POOL_EMPTY,
        AGENT_EVICTED
    }

    public static class AgentOrchestratorEvent {
        private final AgentOrchestratorEventType type;
        private final Map<String, Object> data;
        private final Instant timestamp;

        public AgentOrchestratorEvent(AgentOrchestratorEventType type, Map<String, Object> data, Instant timestamp) {
            this.type = type;
            this.data = data;
            this.timestamp = timestamp;
        }

        public AgentOrchestratorEventType getType() {
            return type;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("AgentOrchestratorEvent{type=%s, data=%s, timestamp=%s}",
                    type, data, timestamp);
        }
    }
}
