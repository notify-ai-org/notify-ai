package com.example.agent;

import com.example.agent.enums.AgentStage;
import com.example.agent.models.AgentContext;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import io.lettuce.core.api.StatefulRedisConnection;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

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

    // Configuration
    private final int maxPoolSize;

    // Event emission
    private final Subject<AgentStageChangeEvent> globalStageChangeSubject;
    private final Subject<AgentOrchestratorEvent> orchestratorEventSubject;

    // Statistics
    private final AtomicInteger totalTasksExecuted;
    private final AtomicInteger totalAgentsCreated;
    private final AtomicInteger totalAgentsTerminated;

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
        // Default: 10 agents, 5 min timeout, auto cleanup
        this.maxPoolSize = 10;
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

        this.globalStageChangeSubject = PublishSubject.create();
        this.orchestratorEventSubject = PublishSubject.create();

        this.totalTasksExecuted = new AtomicInteger(0);
        this.totalAgentsCreated = new AtomicInteger(0);
        this.totalAgentsTerminated = new AtomicInteger(0);

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
        AgentContext agentContext = AgentContextHolder.getContext();
        BaseArtifactService artifactService = new InMemoryArtifactService();
        if (taskId == null)
            taskId = UUID.randomUUID().toString();

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

        return agent.execute(context, taskId, prompt)
                .doOnComplete(() -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
                    executorRegistry
                            .get(ExecutorType.LLM)
                            .submit(() -> logToMemoryAgentWorker.run());
                })
                .doOnError(error -> {
                    busyAgents.remove(agentId);
                    availableAgents.put(agentId, agent);
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
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down agent orchestrator...");

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
