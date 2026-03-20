package com.example.agent.consumers;

import com.example.agent.AgentContextHolder;
import com.example.agent.AgentOrchestrator;
import com.example.agent.EventCaptureRepository;
import com.example.agent.EventRepository;
import com.example.agent.EventScheduleRepository;
import com.example.agent.MessageTemplateRepository;
import com.example.agent.NotificationDispatcher;
import com.example.agent.NotificationJobRepository;
import com.example.agent.models.AgentContext;
import com.example.agent.models.CaptureStatus;
import com.example.agent.models.Event;
import com.example.agent.models.EventCapture;
import com.example.agent.models.EventSchedule;
import com.example.agent.models.MessageTemplate;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.subject.Subject;
import com.example.agent.util.ObjectMapperFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import com.example.agent.config.AgentRegistry;
import com.example.agent.exceptions.ValidationRequiredException;
import com.example.agent.exceptions.AgentApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import com.example.agent.annotations.ManagedConfiguration;
import com.example.agent.annotations.ManagedConfiguration.ConfigSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/event")
public class EventConsumer {
    private final ObjectMapper mapper = ObjectMapperFactory.create();
    private final EventCaptureRepository eventCaptureRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationJobRepository notificationJobRepository;
    private final AgentRegistry agentRegistry;
    private final EventRepository eventRepository;

    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    // Configurable timeout or backpressure parameters
    @Value("${agent.buffer.timeout:15s}")
    @ManagedConfiguration(key = "agent.buffer.timeout", source = ConfigSource.CONFIG_MAP)
    private Duration bufferTimeout;

    public EventConsumer(
            EventCaptureRepository eventCaptureRepository,
            EventScheduleRepository eventScheduleRepository, MessageTemplateRepository messageTemplateRepository,
            AgentOrchestrator agentOrchestrator,
            NotificationDispatcher notificationDispatcher, NotificationJobRepository notificationJobRepository,
            AgentRegistry agentRegistry, EventRepository eventRepository) {
        this.eventCaptureRepository = eventCaptureRepository;
        this.eventScheduleRepository = eventScheduleRepository;
        this.messageTemplateRepository = messageTemplateRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.notificationDispatcher = notificationDispatcher;
        this.notificationJobRepository = notificationJobRepository;
        this.agentRegistry = agentRegistry;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void onMessage(String raw) {
        try {
            List<EventCapture> captures = mapper.readValue(raw, new TypeReference<List<EventCapture>>() {
            });
            enqueueEventProcessing(captures);
        } catch (Exception e) {
            throw new AgentApplicationException("Failed processing vocabulary event", e);
        }
    }

    /**
     * REST endpoint to process event captures.
     * Enqueues event processing tasks and returns immediately.
     */
    @PostMapping
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> processEvents(@RequestBody List<EventCapture> request) {
        if (request == null || request.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Event captures are required", "status", "BAD_REQUEST")));
        }

        enqueueEventProcessing(request);

        return Mono.just(ResponseEntity.accepted().body(Map.of(
                "message", "Event processing enqueued for " + request.size() + " captures",
                "status", "ENQUEUED")));
    }

    /**
     * Enqueue each event capture for processing by the event processor agent.
     * Results are handled asynchronously via callbacks.
     */
    private void enqueueEventProcessing(List<EventCapture> captures) {
        for (EventCapture capture : captures) {
            if (capture == null || capture.getEvent() == null) {
                throw new RuntimeException("Event capture is required with an eventName");
            }
            if (capture.getEvent().getEventType() == null) {
                throw new RuntimeException("Event capture is required with an eventType");
            }

            capture.setId(null);
            capture.setStatus(CaptureStatus.PROCESSING);

            // Persist the Event if it doesn't already exist
            Event event = capture.getEvent();
            if (event.getName() != null) {
                Event existing = eventRepository.findByName(event.getName()).orElse(null);
                if (existing != null) {
                    existing.setEventType(event.getEventType());
                    capture.setEvent(existing);
                } else {
                    event.setStatus(Event.EventStatus.NEW);
                    eventRepository.save(event);
                }
            }

            eventCaptureRepository.save(capture);

            try {
                Map<String, Object> agentInput = buildAgentInput(capture);
                String inputJson = mapper.writeValueAsString(agentInput);
                logger.info("Agent Input JSON: " + inputJson);

                Content prompt = Content.fromParts(
                        Part.fromText("Process the following event capture and determine if it should be emitted:"),
                        Part.fromText(inputJson));

                // Enqueue the event processor task with a callback to handle results
                agentOrchestrator.executeTaskWithAgent(
                        agentRegistry.get(AgentRegistry.EVENT_PROCESSOR_AGENT_ID),
                        null, prompt,
                        flowable -> handleEventProcessorResult(flowable, capture));

            } catch (Exception e) {
                logger.error("Error enqueuing event processing: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Callback that handles the event processor agent's result.
     * Parses the agent output and enqueues schedule/template generation tasks.
     */
    private void handleEventProcessorResult(io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> flowable,
            EventCapture capture) {
        flowable.subscribe(agentEvent -> {
            if (agentEvent.content().isEmpty()
                    || agentEvent.content().get().parts().isEmpty()
                    || agentEvent.content().get().parts().get().isEmpty()) {
                return;
            }

            Optional<List<Part>> parts = agentEvent.content().get().parts();
            if (!parts.isPresent()) {
                return;
            }

            for (Part part : parts.get()) {
                if (!part.text().isPresent()) {
                    continue;
                }
                String json = part.text().get();
                logger.info("Agent Output JSON: " + json);

                List<Map<String, Object>> processedEvents;
                try {
                    processedEvents = mapper.readValue(json,
                            new TypeReference<List<Map<String, Object>>>() {
                            });
                } catch (JsonProcessingException e) {
                    logger.warn("Skipping non-JSON part: " + json);
                    continue;
                }

                for (Map<String, Object> processedEvent : processedEvents) {
                    String resultStatus = (String) processedEvent.get("result");
                    if ("suppressed".equalsIgnoreCase(resultStatus)) {
                        logger.info("Event '{}' suppressed by agent.", capture.getEvent().getName());
                        continue;
                    }

                    Object eventTypeObj = processedEvent.get("eventType");
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> channels = (List<Map<String, String>>) processedEvent
                            .get("channels");
                    if (channels != null) {
                        for (Map<String, String> channelInfo : channels) {
                            String channel = channelInfo.get("channel");

                            List<MessageTemplate> templates = messageTemplateRepository
                                    .findByEventType(capture.getEvent().getName());
                            MessageTemplate template = templates.stream()
                                    .filter(t -> t.getChannel().equalsIgnoreCase(channel))
                                    .findFirst()
                                    .orElse(null);

                            AgentContext agentContext = AgentContextHolder.getContext();
                            List<Subject> subjects = capture.getSubjectResult() != null
                                    ? capture.getSubjectResult().getSubjects()
                                    : null;

                            Map<String, String> attributes = new java.util.HashMap<>();
                            if (capture.getPayload() != null) {
                                capture.getPayload().forEach(
                                        (k, v) -> attributes.put(k, String.valueOf(v)));
                            }

                            NotificationJob job = NotificationJob.builder()
                                    .id(UUID.randomUUID().toString())
                                    .channel(channel)
                                    .idempotencyKey(agentContext.getIdempotencyKey())
                                    .schemaVersion(agentContext.getSchemaVersion())
                                    .source(agentContext.getSource())
                                    .correlationId(agentContext.getCorrelationId())
                                    .eventType(capture.getEvent().getEventType())
                                    .eventName(capture.getEvent().getName())
                                    .priority(NotificationJob.NotificationPriority.NORMAL)
                                    .dispatchMode(NotificationJob.DispatchMode.EVENT)
                                    .subjects(subjects)
                                    .attributes(attributes)
                                    .template(template != null ? template.getTemplate() : null)
                                    .build();

                            // Enqueue schedule and template generation tasks
                            enqueueScheduleAndTemplateGeneration(capture, job, eventTypeObj, template);
                        }
                    }
                }
            }
        }, error -> {
            logger.error("Event processor agent call failed: " + error.getMessage(), error);
        });
    }

    /**
     * Enqueue schedule generation and template generation as separate tasks.
     */
    private void enqueueScheduleAndTemplateGeneration(EventCapture capture, NotificationJob job,
            Object eventTypeObj, MessageTemplate template) {
        try {
            if (template == null && !"deferred".equals(capture.getEvent().getEventType())) {
                // Generate templates, then enqueue schedule generation
                enqueueTemplateGeneration(capture, job, eventTypeObj, () -> {
                    try {
                        List<MessageTemplate> newTemplates = messageTemplateRepository
                                .findByEventType(capture.getEvent().getName());
                        MessageTemplate newTemplate = newTemplates.stream()
                                .filter(t -> t.getChannel().equalsIgnoreCase(job.getChannel()))
                                .findFirst()
                                .orElse(null);
                        
                        if (newTemplate != null) {
                            job.setTemplate(newTemplate.getTemplate());
                        }

                        enqueueScheduleGeneration(capture, job, eventTypeObj, newTemplate);
                    } catch (Exception e) {
                        logger.error("Error enqueuing schedule generation after template generation: " + e.getMessage(), e);
                    }
                });
            } else {
                if (template != null) {
                    logger.info("Templates already exist for event: {}", capture.getEvent().getName());
                }
                enqueueScheduleGeneration(capture, job, eventTypeObj, template);
            }
        } catch (Exception e) {
            logger.error("Error enqueuing schedule/template generation: " + e.getMessage(), e);
        }
    }

    private void enqueueScheduleGeneration(EventCapture capture, NotificationJob job,
            Object eventTypeObj, MessageTemplate template) throws JsonProcessingException {
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

        agentOrchestrator.executeTaskWithAgent(
                agentRegistry.get(AgentRegistry.EVENT_SCHEDULER_AGENT_ID),
                null, prompt,
                flowable -> handleScheduleResult(flowable, capture, job, eventTypeObj, template));
    }

    private void enqueueTemplateGeneration(EventCapture capture, NotificationJob job,
            Object eventTypeObj, Runnable onComplete) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate message templates for the following event:\n\n");
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventName", capture.getEvent().getName());
        payload.put("description", capture.getEvent().getDescription());
        payload.put("payload", capture.getPayload());
        payload.put("occuredAt", capture.getOccuredAt() != null ? capture.getOccuredAt().toString() : null);
        String jsonPayload = mapper.writeValueAsString(payload);
        Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));

        agentOrchestrator.executeTaskWithAgent(
                agentRegistry.get(AgentRegistry.MESSAGE_TEMPLATE_AGENT_ID),
                null, prompt,
                flowable -> handleTemplateResult(flowable, capture, onComplete));
    }

    /**
     * Handle schedule generation agent result.
     */
    private void handleScheduleResult(io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> flowable,
            EventCapture capture, NotificationJob job, Object eventTypeObj, MessageTemplate template) {
        flowable.subscribe(agentEvent -> {
            if (agentEvent.content().isEmpty()
                    || agentEvent.content().get().parts().isEmpty()
                    || agentEvent.content().get().parts().get().isEmpty()) {
                return;
            }
            for (Part part : agentEvent.content().get().parts().get()) {
                if (!part.text().isPresent()) {
                    continue;
                }
                String json = part.text().get();
                List<Map<String, String>> schedules;
                try {
                    schedules = mapper.readValue(json,
                            new TypeReference<List<Map<String, String>>>() {
                            });
                } catch (Exception e) {
                    logger.error("Error parsing schedule JSON: {}", json, e);
                    continue;
                }

                for (Map<String, String> scheduleMap : schedules) {
                    EventSchedule eventSchedule = new EventSchedule();
                    eventSchedule.setId(UUID.randomUUID().toString());
                    eventSchedule.setDescription(scheduleMap.get("scheduleDescription"));
                    eventSchedule.setEventName(capture.getEvent().getName());
                    if (scheduleMap.containsKey("triggerValue")
                            && "cron".equals(scheduleMap.get("triggerType"))) {
                        eventSchedule.setCronExpression(scheduleMap.get("triggerValue"));
                    }
                    eventSchedule.setScheduledAt(Instant.now());
                    eventScheduleRepository.save(eventSchedule);

                    if (!eventSchedule.isValidated()) {
                        logger.warn(
                                "EventSchedule for '{}' (ID: {}) saved but not scheduled - requires validation",
                                capture.getEvent().getName(), eventSchedule.getId());
                    } else {
                        notificationDispatcher.scheduleJob(eventSchedule);
                        logger.info("Scheduled validated EventSchedule for '{}' (ID: {})",
                                capture.getEvent().getName(), eventSchedule.getId());
                    }
                    notificationJobRepository.save(job);
                }

                // Handle static event dispatch
                if (eventTypeObj != null && eventTypeObj.equals("static")) {
                    if (!capture.getEvent().isValidated()) {
                        logger.warn("Skipping dispatch for event '{}' (ID: {}) - not validated",
                                capture.getEvent().getName(), capture.getEvent().getId());
                        return;
                    }
                    if (template != null && !template.isValidated()) {
                        logger.warn("Skipping dispatch for event '{}' - template (ID: {}) not validated",
                                capture.getEvent().getName(), template.getId());
                        return;
                    }
                    try {
                        notificationDispatcher.pushJob(job);
                        notificationJobRepository.save(job);
                        capture.setStatus(CaptureStatus.DISPATCHED);
                        logger.info("Dispatched job for validated event '{}'", capture.getEvent().getName());
                    } catch (ValidationRequiredException e) {
                        logger.error("Dispatch blocked: {}", e.getMessage());
                    }
                }
            }
        }, error -> {
            logger.error("Schedule generation agent failed: " + error.getMessage(), error);
        });
    }

    /**
     * Handle template generation agent result.
     */
    private void handleTemplateResult(io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> flowable,
            EventCapture capture, Runnable onComplete) {
        flowable.subscribe(agentEvent -> {
            if (agentEvent.content().isEmpty()
                    || agentEvent.content().get().parts().isEmpty()
                    || agentEvent.content().get().parts().get().isEmpty()) {
                return;
            }
            for (Part part : agentEvent.content().get().parts().get()) {
                if (!part.text().isPresent()) {
                    continue;
                }
                String json = part.text().get();
                List<Map<String, String>> templateList;
                try {
                    templateList = mapper.readValue(json,
                            new TypeReference<List<Map<String, String>>>() {
                            });
                } catch (Exception e) {
                    logger.error("Failed to parse message templates from JSON: {}", json, e);
                    continue;
                }
                for (Map<String, String> templateMap : templateList) {
                    MessageTemplate messageTemplate = new MessageTemplate();
                    messageTemplate.setChannel(templateMap.get("channel"));
                    messageTemplate.setSubject(templateMap.get("subject"));
                    messageTemplate.setTemplate(templateMap.get("template"));
                    messageTemplate.setEventName(capture.getEvent().getName());
                    messageTemplateRepository.save(messageTemplate);
                }
            }
        }, error -> {
            logger.error("Template generation agent failed: " + error.getMessage(), error);
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Builds a clean, schema-aligned input map for the EventProcessor agent.
     */
    private Map<String, Object> buildAgentInput(EventCapture capture) {
        Map<String, Object> input = new java.util.LinkedHashMap<>();

        input.put("id", capture.getId());
        input.put("timestamp", capture.getTimestamp() != null ? capture.getTimestamp().toString() : null);
        input.put("correlationId", capture.getCorrelationId());

        if (capture.getEvent() != null) {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("name", capture.getEvent().getName());
            event.put("description", capture.getEvent().getDescription());
            event.put("eventType", capture.getEvent().getEventType());
            event.put("priority", capture.getEvent().getPriority());
            event.put("scheduleIntent", capture.getEvent().getScheduleIntent());
            event.put("preferredTimeWindow", capture.getEvent().getPreferredTimeWindow());
            event.values().removeIf(java.util.Objects::isNull);
            input.put("event", event);
        }

        input.put("payload", capture.getPayload());
        input.put("callStack", capture.getCallStack());
        input.put("result", capture.getResult());
        input.put("exception", capture.getException());
        input.put("durationMillis", capture.getDurationMillis());
        input.put("serviceName", capture.getServiceName());

        input.values().removeIf(java.util.Objects::isNull);
        return input;
    }
}
