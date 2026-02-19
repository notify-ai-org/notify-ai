package com.example.agent.consumers;

import com.example.agent.AgentContextHolder;
import com.example.agent.AgentOrchestrator;
import com.example.agent.AgentOrchestrator.AgentTaskContext;
import com.example.agent.EventRepository;
import com.example.agent.EventCaptureRepository;
import com.example.agent.EventScheduleRepository;
import com.example.agent.MessageTemplateRepository;
import com.example.agent.NotificationDispatcher;
import com.example.agent.NotificationJobRepository;
import com.example.agent.interfaces.PromptAssembler;
import com.example.agent.interfaces.RetrievalPlanner;
import com.example.agent.models.AgentContext;
import com.example.agent.models.EventCapture;
import com.example.agent.models.EventSchedule;
import com.example.agent.models.MessageTemplate;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.NotificationJob.NotificationPriority;
import com.example.agent.enums.DecisionType;
import com.example.agent.records.DecisionRequest;
import com.example.agent.records.EventRef;
import com.example.agent.records.PromptPackage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.agent.config.ObjectMapperFactory;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.example.agent.config.AgentRegistry;
import com.example.agent.exceptions.ValidationRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.core.Flowable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
// import org.springframework.kafka.annotation.KafkaListener; // Kafka disabled
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/event")
public class EventConsumer {
    private final ObjectMapper mapper = ObjectMapperFactory.create();
    private final EventRepository repo;
    private final EventCaptureRepository eventCaptureRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationJobRepository notificationJobRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final RetrievalPlanner planner;

    private final PromptAssembler assembler;
    private final AgentRegistry agentRegistry;

    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    // Configurable timeout or backpressure parameters
    @Value("${agent.buffer.timeout:15s}")
    private Duration bufferTimeout;

    public EventConsumer(
            EventRepository repo, EventCaptureRepository eventCaptureRepository,
            EventScheduleRepository eventScheduleRepository, MessageTemplateRepository messageTemplateRepository,
            AgentOrchestrator agentOrchestrator, RetrievalPlanner planner, PromptAssembler assembler,
            NotificationDispatcher notificationDispatcher, NotificationJobRepository notificationJobRepository,
            AgentRegistry agentRegistry) {
        this.repo = repo;
        this.eventCaptureRepository = eventCaptureRepository;
        this.eventScheduleRepository = eventScheduleRepository;
        this.messageTemplateRepository = messageTemplateRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.notificationDispatcher = notificationDispatcher;
        this.notificationJobRepository = notificationJobRepository;
        this.planner = planner;
        this.assembler = assembler;
        this.agentRegistry = agentRegistry;
    }

    // @KafkaListener(topics = "${vocab.kafka-topic}", groupId = "vocab-adk-group")
    // // Kafka disabled
    @Transactional
    public void onMessage(String raw) {
        try {
            List<EventCapture> captures = mapper.readValue(raw, new TypeReference<List<EventCapture>>() {
            });
            Flux.from(processEventsWithAgent(captures))
                    .collectList()
                    .subscribe(
                            events -> System.out.println("Processed " + events.size() + " event selection results"),
                            error -> System.err.println("Error processing events: " + error.getMessage()));
        } catch (Exception e) {
            // STEP 6️⃣: Let Kafka retry via Spring's error handler
            throw new RuntimeException("Failed processing vocabulary event", e);
        }
    }

    /**
     * REST endpoint to process event captures.
     * Accepts POST requests with event capture data.
     *
     * @param request The list of event captures
     * @return Mono<ResponseEntity> with status
     */
    @PostMapping
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> processEvents(@RequestBody List<EventCapture> request) {
        if (request == null || request.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Event captures are required", "status", "BAD_REQUEST")));
        }

        Flux.from(processEventsWithAgent(request))
                .collectList()
                .subscribe(
                        events -> System.out.println("REST: Processed " + events.size() + " event selection results"),
                        error -> System.err.println("REST: Error processing events: " + error.getMessage()));

        return Mono.just(ResponseEntity.accepted().body(Map.of(
                "message", "Event processing initiated for " + request.size() + " captures",
                "status", "PROCESSING")));
    }

    private Flowable<com.google.adk.events.Event> processEventsWithAgent(List<EventCapture> captures) {
        try {
            List<Flowable<com.google.adk.events.Event>> flows = new java.util.ArrayList<>();
            for (EventCapture capture : captures) {
                if (capture == null || capture.getEvent() == null) {
                    throw new RuntimeException("Event capture is required with an eventName");
                }
                if (capture.getEvent().getEventType() == null) {
                    throw new RuntimeException("Event capture is required with an eventType");
                }
                if (capture.getOccuredAt() == null) {
                    throw new RuntimeException("Event capture is required with an occuredAt");
                }
                if (capture.getPayload() == null) {
                    throw new RuntimeException("Event capture is required with a payload");
                }

                DecisionRequest decisionRequest = new DecisionRequest(
                        capture.getTenantId(),
                        DecisionType.SCHEDULE,
                        new ArrayList<>(),
                        new EventRef(capture.getId(),
                                capture.getEvent() != null ? capture.getEvent().getEventType() : "UNKNOWN", "INFO",
                                capture.getTimestamp()),
                        7,
                        2000,
                        5000,
                        "en-US",
                        "UTC");
                PromptPackage promptPackage = assembler.assemble(decisionRequest, planner.plan(decisionRequest));
                String prompt = promptPackage.systemPrompt() + "\n" + promptPackage.userPrompt();

                Flowable<com.google.adk.events.Event> flow = agentOrchestrator.executeTaskWithAgent(
                        agentRegistry.get(AgentRegistry.EVENT_PROCESSOR_AGENT_ID),
                        null,
                        null,
                        Content.fromParts(Part.fromText(prompt), Part.fromText(mapper.writeValueAsString(capture))))
                        .doOnNext(agentEvent -> {
                            if (agentEvent.content().isPresent()
                                    && agentEvent.content().get().parts().isPresent()
                                    && !agentEvent.content().get().parts().get().isEmpty()) {
                                try {
                                    Optional<List<Part>> parts = agentEvent.content().get().parts();
                                    if (!parts.isPresent()) {
                                        return;
                                    }
                                    for (Part part : parts.get()) {
                                        if (part.text().isPresent()) {
                                            String json = part.text().get();
                                            // Parse as array of processed events
                                            List<Map<String, Object>> processedEvents = mapper.readValue(json,
                                                    new TypeReference<List<Map<String, Object>>>() {
                                                    });
                                            for (Map<String, Object> processedEvent : processedEvents) {
                                                Object eventTypeObj = processedEvent.get("eventType");
                                                @SuppressWarnings("unchecked")
                                                List<Map<String, String>> channels = (List<Map<String, String>>) processedEvent
                                                        .get("channels");
                                                if (channels != null) {
                                                    for (Map<String, String> channelInfo : channels) {
                                                        String channel = channelInfo.get("channel");

                                                        // Find template for this event and channel
                                                        List<MessageTemplate> templates = messageTemplateRepository
                                                                .findByEventType(capture.getEvent().getName());

                                                        MessageTemplate template = templates.stream()
                                                                .filter(t -> t.getChannel()
                                                                        .equalsIgnoreCase(channel))
                                                                .findFirst()
                                                                .orElse(null);

                                                        AgentContext agentContext = AgentContextHolder.getContext();
                                                        // Create notification job
                                                        NotificationJob.NotificationJobBuilder jobBuilder = NotificationJob
                                                                .builder()
                                                                .id(UUID.randomUUID().toString())
                                                                .channel(channel)
                                                                .idempotencyKey(agentContext.getIdempotencyKey())
                                                                .schemaVersion(agentContext.getSchemaVersion())
                                                                .source(agentContext.getSource())
                                                                .correlationId(agentContext.getCorrelationId())
                                                                .eventType(capture.getEvent().getEventType())
                                                                .priority(NotificationPriority.valueOf(
                                                                        processedEvent.get("priority").toString()))
                                                                .dispatchMode(NotificationJob.DispatchMode.EVENT);

                                                        if (template != null) {
                                                            jobBuilder.template(template.getTemplate());
                                                        } else {
                                                            throw new Exception("Cannot find template for given job");
                                                        }

                                                        NotificationJob job = jobBuilder.build();
                                                        if (eventTypeObj != null && eventTypeObj.equals("static")) {
                                                            // Validation check before dispatching
                                                            if (!capture.getEvent().isValidated()) {
                                                                logger.warn(
                                                                        "Skipping dispatch for event '{}' (ID: {}) - not validated",
                                                                        capture.getEvent().getName(),
                                                                        capture.getEvent().getId());
                                                                continue;
                                                            }
                                                            if (template != null && !template.isValidated()) {
                                                                logger.warn(
                                                                        "Skipping dispatch for event '{}' - template (ID: {}) not validated",
                                                                        capture.getEvent().getName(), template.getId());
                                                                continue;
                                                            }

                                                            try {
                                                                notificationDispatcher.pushJob(job);
                                                                notificationJobRepository.save(job);
                                                                logger.info("Dispatched job for validated event '{}'",
                                                                        capture.getEvent().getName());
                                                            } catch (ValidationRequiredException e) {
                                                                logger.error("Dispatch blocked: {}", e.getMessage());
                                                            }
                                                        } else {
                                                            generateTemplatesAndSchedules(capture, job);
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error parsing agent output: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }).onErrorResumeNext(error -> {
                            System.err.println("Agent call failed: " + error.getMessage());
                            return Flowable.empty();
                        });
                flows.add(flow);
            }

            if (flows.isEmpty()) {
                return Flowable.empty();
            }
            return Flowable.merge(flows);

        } catch (Exception e) {
            System.err.println("Error invoking agent for events: " + e.getMessage());
            return Flowable.error(e);
        }
    }

    private io.reactivex.rxjava3.disposables.Disposable generateTemplatesAndSchedules(EventCapture capture,
            NotificationJob job) {
        try {
            List<AgentTaskContext> tasks = new ArrayList<>();
            // Generate schedules and templates using agents
            AgentTaskContext scheduleCtx = generateScheduleWithAgent(capture);
            tasks.add(scheduleCtx);
            // Find template for this event and channel
            List<MessageTemplate> templates = messageTemplateRepository.findByEventType(capture.getEvent().getName());
            if (templates.isEmpty()) {
                if (capture.getEvent().getEventType().equals("deferred")) {
                    // Handle deferred events if needed
                } else {
                    AgentTaskContext templateCtx = generateMessageTemplatesWithAgent(capture);
                    tasks.add(templateCtx);
                }
            }
            return agentOrchestrator.executeTasksSequentially(tasks)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribe(
                            events -> {
                                for (com.google.adk.events.Event agentEvent : events) {
                                    if (agentEvent.content().isPresent()
                                            && agentEvent.content().get().parts().isPresent()
                                            && !agentEvent.content().get().parts().get().isEmpty()) {
                                        try {
                                            Optional<List<Part>> parts = agentEvent.content().get().parts();
                                            if (!parts.isPresent()) {
                                                continue;
                                            }

                                            switch (agentEvent.author()) {
                                                case "Event Notification Scheduler Agent":
                                                    for (Part part : parts.get()) {
                                                        if (part.text().isPresent()) {
                                                            String json = part.text().get();
                                                            // Parse as array of schedule objects
                                                            List<Map<String, String>> schedules = mapper.readValue(json,
                                                                    new TypeReference<List<Map<String, String>>>() {
                                                                    });

                                                            for (Map<String, String> scheduleMap : schedules) {
                                                                EventSchedule eventSchedule = new EventSchedule();
                                                                eventSchedule.setId(UUID.randomUUID().toString());
                                                                eventSchedule.setDescription(
                                                                        scheduleMap.get("scheduleDescription"));
                                                                eventSchedule
                                                                        .setEventName(capture.getEvent().getName());
                                                                if (scheduleMap.containsKey("triggerValue")
                                                                        && scheduleMap.get("triggerType")
                                                                                .equals("cron")) {
                                                                    eventSchedule.setCronExpression(
                                                                            scheduleMap.get("triggerValue"));
                                                                }
                                                                eventSchedule.setScheduledAt(Instant.now());
                                                                eventScheduleRepository.save(eventSchedule);

                                                                // Validation check: only schedule if validated
                                                                // (Note: scheduleJob() also checks, this is a safety
                                                                // guard)
                                                                if (!eventSchedule.isValidated()) {
                                                                    logger.warn(
                                                                            "EventSchedule for '{}' (ID: {}) saved but not scheduled - requires validation",
                                                                            capture.getEvent().getName(),
                                                                            eventSchedule.getId());
                                                                } else {
                                                                    // dispatch deferred event schedule
                                                                    notificationDispatcher.scheduleJob(eventSchedule);
                                                                    logger.info(
                                                                            "Scheduled validated EventSchedule for '{}' (ID: {})",
                                                                            capture.getEvent().getName(),
                                                                            eventSchedule.getId());
                                                                }
                                                                // save job
                                                                notificationJobRepository.save(job);
                                                            }
                                                        }
                                                    }
                                                    break;
                                                case "MessageTemplateGenerator":
                                                    for (Part part : parts.get()) {
                                                        if (part.text().isPresent()) {
                                                            String json = part.text().get();
                                                            // Parse as array of template objects
                                                            List<Map<String, String>> templateList = mapper.readValue(
                                                                    json,
                                                                    new TypeReference<List<Map<String, String>>>() {
                                                                    });

                                                            for (Map<String, String> templateMap : templateList) {
                                                                MessageTemplate messageTemplate = new MessageTemplate();
                                                                messageTemplate.setChannel(templateMap.get("channel"));
                                                                messageTemplate.setSubject(templateMap.get("subject"));
                                                                messageTemplate
                                                                        .setTemplate(templateMap.get("template"));
                                                                messageTemplate
                                                                        .setEventName(capture.getEvent().getName());
                                                                messageTemplateRepository.save(messageTemplate);
                                                            }
                                                        }
                                                    }
                                                    break;
                                                default:
                                                    System.out.println("Unknown agent source: " + agentEvent.author());
                                            }
                                        } catch (Exception e) {
                                            System.err.println("Error parsing agent output: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }, error -> System.err.println("Agent call failed: " + error.getMessage()));
        } catch (JsonProcessingException e) {
            System.err.println("Error generating templates and schedules: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate a schedule for an event using the Event Notification Scheduler
     * Agent.
     *
     * @param classes The list of class models representing the events
     * @return AgentTaskContext for the agent execution
     * @throws JsonProcessingException
     */
    private AgentTaskContext generateScheduleWithAgent(EventCapture capture) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate notification schedules for the following events:\n\n");
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventName", capture.getEvent().getName());
        payload.put("eventDescription", capture.getEvent().getDescription());
        payload.put("occuredAt", capture.getOccuredAt().toString());
        payload.put("scheduleIntent", capture.getEvent().getScheduleIntent());
        payload.put("preferredTimeWindow", capture.getEvent().getPreferredTimeWindow());
        String jsonPayload = mapper.writeValueAsString(payload);
        Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));
        return agentOrchestrator.new AgentTaskContext(agentRegistry.get(AgentRegistry.EVENT_SCHEDULER_AGENT_ID), null,
                null, prompt);
    }

    /**
     * Generate message templates for an event using the Message Template Generator
     * Agent.
     *
     * @param classes The list of class models representing the events
     * @return AgentTaskContext for the agent execution
     * @throws JsonProcessingException
     */
    private AgentTaskContext generateMessageTemplatesWithAgent(EventCapture capture)
            throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate message templates for the following event:\n\n");

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventName", capture.getEvent().getName());
        payload.put("description", capture.getEvent().getDescription());
        payload.put("payload", capture.getPayload());
        payload.put("occuredAt", capture.getOccuredAt() != null ? capture.getOccuredAt().toString() : null);

        // add user instruction prompts

        String jsonPayload = mapper.writeValueAsString(payload);
        Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));
        return agentOrchestrator.new AgentTaskContext(agentRegistry.get(AgentRegistry.MESSAGE_TEMPLATE_AGENT_ID), null,
                null, prompt);
    }

}
