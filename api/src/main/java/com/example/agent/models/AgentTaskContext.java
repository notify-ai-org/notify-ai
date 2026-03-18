package com.example.agent.models;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;

import java.util.function.Consumer;

/**
 * Data class encapsulating an agent task context for sequential/parallel
 * execution via the AgentOrchestrator.
 */
public class AgentTaskContext {
    public final String agentType;
    public final String taskId;
    public final Content prompt;
    public final Consumer<Flowable<Event>> resultCallback;

    public AgentTaskContext(String agentType, String taskId, Content prompt,
            Consumer<Flowable<Event>> resultCallback) {
        this.agentType = agentType;
        this.taskId = taskId;
        this.prompt = prompt;
        this.resultCallback = resultCallback;
    }
}
