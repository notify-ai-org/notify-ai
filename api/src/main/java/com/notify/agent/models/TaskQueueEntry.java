package com.notify.agent.models;

import com.notify.agent.AgentContextHolder;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a task waiting to be dispatched to an agent.
 */
public class TaskQueueEntry {
    private final String agentType; // null means "any type"
    private final String taskId;
    private final Content prompt;
    private final Consumer<Flowable<Event>> resultCallback;
    private final Instant enqueuedAt;
    private final AgentContext context;

    public TaskQueueEntry(String agentType, String taskId, Content prompt,
            Consumer<Flowable<Event>> resultCallback) {
        this.agentType = agentType;
        this.taskId = taskId != null ? taskId : UUID.randomUUID().toString();
        this.prompt = prompt;
        this.resultCallback = resultCallback;
        this.enqueuedAt = Instant.now();
        this.context = AgentContextHolder.getContext();
    }

    public String getAgentType() {
        return agentType;
    }

    public String getTaskId() {
        return taskId;
    }

    public Content getPrompt() {
        return prompt;
    }

    public Consumer<Flowable<Event>> getResultCallback() {
        return resultCallback;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public AgentContext getContext() {
        return context;
    }
}
