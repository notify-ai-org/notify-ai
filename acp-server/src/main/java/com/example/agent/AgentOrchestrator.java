package com.example.agent;

import com.example.agent.enums.AgentStage;
import com.example.agent.models.AgentContext;
import com.example.agent.models.AgentSnapshot;
import com.example.agent.models.AgentStageChangeEvent;
import com.example.agent.service.LogToMemoryAgentWorker;
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

    // Agent pool management
    private final Map<String, AgentWrapper> agentPool;
    private final Map<String, AgentWrapper> availableAgents;
    private final Map<String, AgentWrapper> busyAgents;
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
    private final LogToMemoryAgentWorker logToMemoryAgentWorker;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentOrchestrator(AgentSnapshotRepository snapshotRepo, AgentLogRepository logRepo,
            SessionService sessionService, CentralExecutorRegistry executorRegistry,
            LogToMemoryAgentWorker logToMemoryAgentWorker,
            StatefulRedisConnection<String, String> redisConnection) {
        this.snapshotRepo = snapshotRepo;
        this.logRepo = logRepo;
        this.sessionService = sessionService;
        this.executorRegistry = executorRegistry;
        this.logToMemoryAgentWorker = logToMemoryAgentWorker;
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
    public String registerAgent(BaseAgent agent) {
        if (agentPool.size() >= maxPoolSize) {
            throw new IllegalStateException("Agent pool is full. Max size: " + maxPoolSize);
        }

        String agentId = "agent_" + nextAgentId.getAndIncrement();
        AgentWrapper wrapper = new AgentWrapper(agentId, agent, snapshotRepo, logRepo, redisConnection, sessionService);

        agentPool.put(agentId, wrapper);
        availableAgents.put(agentId, wrapper);

        // Track overflow agents (those registered beyond the protected core pool)
        if (agentPool.size() > corePoolSize) {
            overflowAgentIds.add(agentId);
        }

        // Subscribe to agent's stage changes
        wrapper.getStageChangeObservable().subscribe(globalStageChangeSubject::onNext);

        // Initialize agent
        wrapper.transitionTo(AgentStage.INITIALIZING, "Agent registered", Map.of("agentName", agent.name()));
        wrapper.transitionTo(AgentStage.READY, "Agent initialized", Map.of());

        logger.info(String.format("Registered agent %s (%s) in pool. Pool size: %d",
                agentId, agent.name(), agentPool.size()));

        emitOrchestratorEvent(AgentOrchestratorEventType.AGENT_REGISTERED,
                Map.of("agentId", agentId, "agentName", agent.name()));

        return agentId;
    }

    /**
     * Execute a task using an available agent
     */
    public Flowable<Event> executeTask(InvocationContext context, String taskId, Content prompt) {
        AgentWrapper agent = getAvailableAgent();
        if (agent == null) {
            return Flowable.error(new IllegalStateException("No available agents in pool"));
        }

        // Move agent from available to busy
        availableAgents.remove(agent.getAgentId());
        busyAgents.put(agent.getAgentId(), agent);

        logger.info(String.format("Executing task %s with agent %s", taskId, agent.getAgentId()));

        return agent.execute(context, taskId, prompt)
                .doOnComplete(() -> {
                    // Move agent back to available
                    busyAgents.remove(agent.getAgentId());
                    availableAgents.put(agent.getAgentId(), agent);
                    agent.transitionTo(AgentStage.READY, "Task completed", Map.of("taskId", taskId));
                })
                .doOnError(error -> {
                    // Move agent back to available even on error
                    busyAgents.remove(agent.getAgentId());
                    availableAgents.put(agent.getAgentId(), agent);
                    agent.transitionTo(AgentStage.READY, "Task failed, agent available",
                            Map.of("taskId", taskId, "error", error.getMessage()));
                });
    }

    /**
     * Execute a task with a specific agent
     */
    /**
     * Execute a task with a specific agent.
     */
    public Flowable<Event> executeTaskWithAgent(String agentId, InvocationContext context, String taskId,
            Content prompt) {
        AgentWrapper agent = agentPool.get(agentId);
        AgentContext agentContext = AgentContextHolder.getContext();
        BaseArtifactService artifactService = new InMemoryArtifactService();
        final String finalTaskId = (taskId == null) ? UUID.randomUUID().toString() : taskId;

        if (agent == null) {
            return Flowable.error(new IllegalArgumentException("Agent not found: " + agentId));
        }
        if (context == null) {
            context = InvocationContext.create(sessionService, artifactService, agent.getAgent(),
                    agentContext.getSession(), null, null);
        }
        if (!agent.isAvailable()) {
            return Flowable.error(new IllegalStateException("Agent not available: " + agentId));
        }

        // Move agent from available to busy
        availableAgents.remove(agentId);
        busyAgents.put(agentId, agent);

        return agent.execute(context, finalTaskId, prompt)
                .doOnComplete(() -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
                    agent.transitionTo(AgentStage.READY, "Task completed", Map.of("taskId", finalTaskId));
                    executorRegistry
                            .get(ExecutorType.LLM)
                            .submit(() -> logToMemoryAgentWorker.run());
                })
                .doOnError(error -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
                    agent.transitionTo(AgentStage.READY, "Task failed, agent available",
                            Map.of("taskId", finalTaskId, "error", error.getMessage()));
                    executorRegistry
                            .get(ExecutorType.LLM)
                            .submit(() -> logToMemoryAgentWorker.run());
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
                    executeTaskWithAgent(atc.agentId, atc.context, atc.taskId, atc.prompt));
        }
        return flowable.doOnComplete(() -> {
            executorRegistry
                    .get(ExecutorType.LLM)
                    .submit(() -> logToMemoryAgentWorker.run());
        });
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
            flowables.add(executeTaskWithAgent(atc.agentId, atc.context, atc.taskId, atc.prompt));
        }
        return Flowable.merge(flowables).doOnComplete(() -> {
            executorRegistry
                    .get(ExecutorType.LLM)
                    .submit(() -> logToMemoryAgentWorker.run());
        });
    }

    /**
     * Helper data class to encapsulate agent call context for sequential/parallel
     * execution.
     */
    public class AgentTaskContext {
        public final String agentId;
        public final InvocationContext context;
        public final String taskId;
        public final Content prompt;

        public AgentTaskContext(String agentId, InvocationContext context, String taskId, Content prompt) {
            this.agentId = agentId;
            this.context = context;
            this.taskId = taskId;
            this.prompt = prompt;
        }
    }

    /**
     * Pause a specific agent
     */
    public boolean pauseAgent(String agentId, String reason) {
        AgentWrapper agent = agentPool.get(agentId);
        if (agent != null) {
            boolean success = agent.pause(reason);
            if (success) {
                availableAgents.remove(agentId);
                busyAgents.remove(agentId);
            }
            return success;
        }
        return false;
    }

    /**
     * Resume a paused agent
     */
    public boolean resumeAgent(String agentId, String reason) {
        AgentWrapper agent = agentPool.get(agentId);
        if (agent != null) {
            boolean success = agent.resume(reason);
            if (success) {
                availableAgents.put(agentId, agent);
            }
            return success;
        }
        return false;
    }

    /**
     * Get all agents and their states
     */
    public Map<String, Map<String, Object>> getAllAgentStates() {
        return agentPool.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            AgentWrapper wrapper = entry.getValue();
                            Map<String, Object> state = new HashMap<>(wrapper.getAgentState());
                            state.put("agentId", wrapper.getAgentId());
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
                .map(w -> w.getAgent().name())
                .collect(Collectors.toSet());

        int restoredCount = 0;
        for (AgentSnapshot snapshot : candidates) {
            String agentName = snapshot.getAgentName();

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

            if (agentPool.size() >= maxPoolSize) {
                logger.warning(String
                        .format("restoreFromSnapshots: pool full (%d/%d), cannot restore '%s'",
                                agentPool.size(), maxPoolSize, agentName));
                break;
            }

            try {
                // Register a fresh wrapper for this agent prototype
                String newAgentId = registerAgent(prototype);
                restoredCount++;
                logger.info(String.format(
                        "restoreFromSnapshots: restored agent '%s' as pool id '%s' (was %s, taskId=%s)",
                        agentName, newAgentId, snapshot.getCurrentStage(), snapshot.getCurrentTaskId()));

                // If the snapshot recorded a pending task, re-submit it
                String pendingTaskId = snapshot.getCurrentTaskId();
                if (pendingTaskId != null && !pendingTaskId.isBlank()) {
                    AgentWrapper wrapper = agentPool.get(newAgentId);
                    if (wrapper != null) {
                        // Build a minimal prompt indicating this is a task replay
                        Content replayPrompt = com.google.genai.types.Content.fromParts(
                                com.google.genai.types.Part.fromText(
                                        "[REPLAY] Resume task: " + pendingTaskId));
                        // executeTaskWithAgent(newAgentId, null, pendingTaskId, replayPrompt)
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
        if (agentPool.size() <= corePoolSize) {
            return; // nothing to evict — pool is within the protected floor
        }

        Instant cutoff = Instant.now().minusSeconds(idleTimeoutSeconds);

        List<String> toEvict = overflowAgentIds.stream()
                .filter(agentPool::containsKey)
                .map(agentPool::get)
                .filter(w -> w.isAvailable() && w.getLastActivityAt().isBefore(cutoff))
                .map(AgentWrapper::getAgentId)
                .collect(Collectors.toList());

        for (String agentId : toEvict) {
            AgentWrapper wrapper = agentPool.remove(agentId);
            if (wrapper == null)
                continue; // already removed concurrently
            availableAgents.remove(agentId);
            busyAgents.remove(agentId);
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
                .filter(AgentWrapper::isAvailable)
                .findFirst()
                .orElse(null);
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
