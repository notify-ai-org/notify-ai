package com.example.agent.controllers;

import com.example.agent.AgentContextHolder;
import com.example.agent.AgentOrchestrator;
import com.example.agent.NotificationDispatcher;
import com.example.agent.config.AgentRegistry;
import com.example.agent.exceptions.AgentApplicationException;
import com.example.agent.models.AgentContext;
import com.example.agent.models.NotificationJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trial")
public class TrialAgentController {

    private static final Logger logger = LoggerFactory.getLogger(TrialAgentController.class);
    private final AgentOrchestrator agentOrchestrator;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BaseAgent trialAgent;

    public TrialAgentController(AgentOrchestrator agentOrchestrator, NotificationDispatcher dispatcher) {
        this.agentOrchestrator = agentOrchestrator;
        this.dispatcher = dispatcher;

        // Define JSON schema for strict LLM output
        Schema subjectsSchema = Schema.builder().type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).build()).build();
        Schema responseSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "message", Schema.builder().type(Type.Known.STRING).build(),
                        "subjects", subjectsSchema))
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        this.trialAgent = LlmAgent.builder()
                .name("trial_agent")
                .model("gemini-2.5-pro")
                .instruction(
                        "You are a Trial Agent demonstrating the Notify-ai capabilities. " +
                                "You ONLY accept explicit prompt requests similar to: 'Send me a hi on given number/email-id on email/SMS/whatsapp'. "
                                +
                                "If the user input is off-topic, output message: 'Sorry, I can only send trial messages via email/SMS/whatsapp' and an empty subjects list. "
                                +
                                "Otherwise, produce a user-friendly message specifying 'Notify-ai' as the sender. Ensure JSON compliance output.")
                .generateContentConfig(config)
                .build();

        // Register the ephemeral agent in orchestrator proxy map pool
        this.agentOrchestrator.registerAgent("TRIAL_AGENT", this.trialAgent);
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> processTrialMessage(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty")));
        }

        Content prompt = Content.fromParts(Part.fromText(userMessage));

        io.reactivex.rxjava3.core.Flowable<Event> flowable = agentOrchestrator.createTaskFlowable(
                "TRIAL_AGENT",
                UUID.randomUUID().toString(),
                prompt,
                AgentContextHolder.getContext() != null ? AgentContextHolder.getContext() : new AgentContext());

        Mono<Event> eventMono = Mono.from(flowable.lastOrError().toFlowable());

        return eventMono.map(event -> {
            if (event.content().isEmpty() || event.content().get().parts().isEmpty()
                    || event.content().get().parts().get().isEmpty()) {
                return (ResponseEntity<Map<String, Object>>) ResponseEntity.internalServerError()
                        .<Map<String, Object>>body(Map.of("error", "Agent returned empty response"));
            }

            Part part = event.content().get().parts().get().get(0);
            if (part.text().isEmpty()) {
                return (ResponseEntity<Map<String, Object>>) ResponseEntity.internalServerError()
                        .<Map<String, Object>>body(Map.of("error", "No text in agent part"));
            }

            String json = part.text().get();
            try {
                Map<String, Object> output = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
                String message = (String) output.get("message");
                @SuppressWarnings("unchecked")
                List<String> subjects = (List<String>) output.get("subjects");

                // Check if it's the off-topic fallback response
                if (message != null && message.contains("Sorry, I can only send trial messages")) {
                    return (ResponseEntity<Map<String, Object>>) ResponseEntity.badRequest()
                            .<Map<String, Object>>body(Map.of("error", message));
                }

                // Inject Trial Job to Notification Dispatcher natively
                NotificationJob job = new NotificationJob();
                job.setEventType("TRIAL_MESSAGE");
                job.setEventName("Trial Demo Trigger");
                job.setSource("TrialAgent");
                job.setChannel("EMAIL"); // Fallback, would natively deduce and parse
                job.setDispatchMode(NotificationJob.DispatchMode.EVENT);
                job.setTemplate(message);

                // Set the specific destination using target parameter from input or fallback
                job.getAttributes().put("trialTarget", userMessage);

                dispatcher.pushJob(job);

                return (ResponseEntity<Map<String, Object>>) ResponseEntity.ok()
                        .<Map<String, Object>>body(Map.of(
                                "status", "DISPATCHED",
                                "jobId", job.getId() != null ? job.getId() : UUID.randomUUID().toString(),
                                "agentResponse", output));

            } catch (Exception e) {
                logger.error("Failed to parse or dispatch Trial Agent output: " + json, e);
                return (ResponseEntity<Map<String, Object>>) ResponseEntity.internalServerError()
                        .<Map<String, Object>>body(Map.of("error", "Failed to process agent payload"));
            }
        }).onErrorReturn(ResponseEntity.internalServerError()
                .<Map<String, Object>>body(Map.of("error", "Timeout or orchestrator execution failure")));
    }
}
