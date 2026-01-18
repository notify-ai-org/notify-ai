package com.example.agent;

import com.example.agent.models.AgentStage;
import com.example.agent.models.AgentStageChangeEvent;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import redis.clients.jedis.JedisPool;

/**
 * Agent orchestrator that manages a pool of agents, maintains state machines,
 * and emits stage change events. Similar to a thread pool executor but for
 * agents.
 * State buckets
 * Prober
 * Batch job execution
 * Efficient job transfer
 */
public class AgentOrchestrator {
    private static final Logger logger = Logger.getLogger(AgentOrchestrator.class.getName());

    // Agent pool management
    private final Map<String, AgentWrapper> agentPool;
    private final Map<String, AgentWrapper> availableAgents;
    private final Map<String, AgentWrapper> busyAgents;
    private final AtomicInteger nextAgentId;

    // Configuration
    private final int maxPoolSize;
    private final long agentTimeoutMillis;
    private final boolean autoCleanup;

    // Event emission
    private final Subject<AgentStageChangeEvent> globalStageChangeSubject;
    private final Subject<AgentOrchestratorEvent> orchestratorEventSubject;

    // Background tasks
    private final ScheduledExecutorService scheduler;
    private final Timer cleanupTimer;

    // Statistics
    private final AtomicInteger totalTasksExecuted;
    private final AtomicInteger totalAgentsCreated;
    private final AtomicInteger totalAgentsTerminated;

    private final AgentSnapshotRepository snapshotRepo;
    private final AgentLogRepository logRepo;
    private final JedisPool jedisPool;

    public AgentOrchestrator(AgentSnapshotRepository snapshotRepo, AgentLogRepository logRepo) {
        this(10, 300000, true, snapshotRepo, logRepo); // Default: 10 agents, 5 min timeout, auto cleanup
    }

    public AgentOrchestrator(int maxPoolSize, long agentTimeoutMillis, boolean autoCleanup,
            AgentSnapshotRepository snapshotRepo, AgentLogRepository logRepo) {
        this.maxPoolSize = maxPoolSize;
        this.agentTimeoutMillis = agentTimeoutMillis;
        this.autoCleanup = autoCleanup;
        this.snapshotRepo = snapshotRepo;
        this.logRepo = logRepo;

        this.agentPool = new ConcurrentHashMap<>();
        this.availableAgents = new ConcurrentHashMap<>();
        this.busyAgents = new ConcurrentHashMap<>();
        this.nextAgentId = new AtomicInteger(1);

        // Initialize JedisPool with default settings (localhost:6379)
        this.jedisPool = new JedisPool("localhost", 6379);

        this.globalStageChangeSubject = PublishSubject.create();
        this.orchestratorEventSubject = PublishSubject.create();

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.cleanupTimer = new Timer("AgentCleanupTimer", true);

        this.totalTasksExecuted = new AtomicInteger(0);
        this.totalAgentsCreated = new AtomicInteger(0);
        this.totalAgentsTerminated = new AtomicInteger(0);

        setupCleanupTask();
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
        AgentWrapper wrapper = new AgentWrapper(agentId, agent, snapshotRepo, logRepo, jedisPool);

        agentPool.put(agentId, wrapper);
        availableAgents.put(agentId, wrapper);
        totalAgentsCreated.incrementAndGet();

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

        totalTasksExecuted.incrementAndGet();

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

        if (taskId == null)
            taskId = UUID.randomUUID().toString();

        if (agent == null) {
            return Flowable.error(new IllegalArgumentException("Agent not found: " + agentId));
        }
        if (context == null) {
            context = InvocationContext.create(null, null, agent.getAgent(), null, null, null);
        }
        if (!agent.isAvailable()) {
            return Flowable.error(new IllegalStateException("Agent not available: " + agentId));
        }

        // Move agent from available to busy
        availableAgents.remove(agentId);
        busyAgents.put(agentId, agent);

        return agent.execute(context, taskId, prompt)
                .doOnComplete(() -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
                })
                .doOnError(error -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
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
            flowables.add(executeTaskWithAgent(atc.agentId, atc.context, atc.taskId, atc.prompt));
        }
        return Flowable.merge(flowables);
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
     * Terminate a specific agent
     */
    public boolean terminateAgent(String agentId, String reason) {
        AgentWrapper agent = agentPool.get(agentId);
        if (agent != null) {
            boolean success = agent.terminate(reason);
            if (success) {
                agentPool.remove(agentId);
                availableAgents.remove(agentId);
                busyAgents.remove(agentId);
                totalAgentsTerminated.incrementAndGet();

                emitOrchestratorEvent(AgentOrchestratorEventType.AGENT_TERMINATED,
                        Map.of("agentId", agentId, "reason", reason));
            }
            return success;
        }
        return false;
    }

    /**
     * Get agent statistics
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
                "totalAgents", agentPool.size(),
                "availableAgents", availableAgents.size(),
                "busyAgents", busyAgents.size(),
                "maxPoolSize", maxPoolSize,
                "totalTasksExecuted", totalTasksExecuted.get(),
                "totalAgentsCreated", totalAgentsCreated.get(),
                "totalAgentsTerminated", totalAgentsTerminated.get());
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
     * Shutdown the orchestrator
     */
    public void shutdown() {
        logger.info("Shutting down agent orchestrator...");

        // Terminate all agents
        agentPool.keySet().forEach(agentId -> terminateAgent(agentId, "Orchestrator shutdown"));

        // Shutdown scheduler
        scheduler.shutdown();
        cleanupTimer.cancel();

        // Complete subjects
        globalStageChangeSubject.onComplete();
        orchestratorEventSubject.onComplete();

        logger.info("Agent orchestrator shutdown complete");

        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    // Private helper methods

    private AgentWrapper getAvailableAgent() {
        return availableAgents.values().stream()
                .filter(AgentWrapper::isAvailable)
                .findFirst()
                .orElse(null);
    }

    private void setupCleanupTask() {
        if (autoCleanup) {
            scheduler.scheduleAtFixedRate(() -> {
                cleanupInactiveAgents();
            }, agentTimeoutMillis, agentTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void cleanupInactiveAgents() {
        Instant cutoff = Instant.now().minusMillis(agentTimeoutMillis);
        agentPool.entrySet().removeIf(entry -> {
            AgentWrapper agent = entry.getValue();
            if (agent.getLastActivityAt().isBefore(cutoff) &&
                    (agent.getCurrentStage() == AgentStage.IDLE || agent.getCurrentStage() == AgentStage.READY)) {
                logger.info(String.format("Cleaning up inactive agent %s", agent.getAgentId()));
                terminateAgent(agent.getAgentId(), "Inactive agent cleanup");
                return true;
            }
            return false;
        });
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
        AGENT_TERMINATED,
        AGENT_STAGE_CHANGED,
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_FAILED,
        POOL_FULL,
        POOL_EMPTY
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
