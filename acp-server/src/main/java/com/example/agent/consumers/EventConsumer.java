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
import com.example.agent.util.ObjectMapperFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import com.example.agent.config.AgentRegistry;
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
     * Uses RxJava Flowables to construct a sequential execution pipeline.
     */
    @SuppressWarnings("null")
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

                // 1. Process Event Task
                io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> eventProcessFlowable = agentOrchestrator
                        .createTaskFlowable(
                                agentRegistry.get(AgentRegistry.EVENT_PROCESSOR_AGENT_ID),
                                UUID.randomUUID().toString(), prompt, AgentContextHolder.getContext());

                // 2. Chain downstream tasks based on processor result
                eventProcessFlowable.flatMap(agentEvent -> {
                    if (agentEvent.content().isEmpty() || agentEvent.content().get().parts().isEmpty()
                            || agentEvent.content().get().parts().get().isEmpty()) {
                        return io.reactivex.rxjava3.core.Flowable.empty();
                    }

                    Optional<List<Part>> parts = agentEvent.content().get().parts();
                    if (!parts.isPresent())
                        return io.reactivex.rxjava3.core.Flowable.empty();

                    List<io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event>> downstreamTasks = new java.util.ArrayList<>();

                    for (Part part : parts.get()) {
                        if (!part.text().isPresent())
                            continue;
                        String json = part.text().get();
                        logger.info("Agent Output JSON: " + json);

                        List<Map<String, Object>> processedEvents;
                        try {
                            processedEvents = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
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
                                    AgentContext agentContext = AgentContextHolder.getContext();
                                    Map<String, String> attributes = new java.util.HashMap<>();
                                    if (capture.getPayload() != null) {
                                        capture.getPayload().forEach((k, v) -> attributes.put(k, String.valueOf(v)));
                                    }

                                    NotificationJob job = NotificationJob.builder()
                                            .id(UUID.randomUUID().toString())
                                            .channel(channel)
                                            .idempotencyKey(
                                                    agentContext != null ? agentContext.getIdempotencyKey() : null)
                                            .schemaVersion(
                                                    agentContext != null ? agentContext.getSchemaVersion() : null)
                                            .source(agentContext != null ? agentContext.getSource() : null)
                                            .correlationId(
                                                    agentContext != null ? agentContext.getCorrelationId() : null)
                                            .eventType(capture.getEvent().getEventType())
                                            .eventName(capture.getEvent().getName())
                                            .priority(NotificationJob.NotificationPriority.NORMAL)
                                            .dispatchMode(NotificationJob.DispatchMode.EVENT)
                                            .subjects(capture.getSubjectResult() != null
                                                    ? capture.getSubjectResult().getSubjects()
                                                    : null)
                                            .attributes(attributes)
                                            .build();

                                    try {
                                        if (job.getAttributes() != null) {
                                            job.setAttributesJson(mapper.writeValueAsString(job.getAttributes()));
                                        }
                                        if (job.getSubjects() != null) {
                                            job.setSubjectsJson(mapper.writeValueAsString(job.getSubjects()));
                                        }
                                    } catch (JsonProcessingException e) {
                                        logger.warn("Failed to stringify job attributes/subjects", e);
                                    }

                                    downstreamTasks.add(
                                            buildScheduleAndTemplateFlowable(capture, job, eventTypeObj));
                                }
                            }
                        }
                    }

                    if (downstreamTasks.isEmpty()) {
                        return io.reactivex.rxjava3.core.Flowable.empty();
                    }
                    // Process channels in parallel via orchestrator API
                    return agentOrchestrator.createParallelFlowable(downstreamTasks);

                }).subscribe(
                        result -> {
                            /* Terminal elements dropped or logged */ },
                        error -> logger.error("Event processing pipeline failed: " + error.getMessage(), error));

            } catch (Exception e) {
                logger.error("Error enqueuing event processing: " + e.getMessage(), e);
            }
        }
    }

    private io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> buildScheduleAndTemplateFlowable(
            EventCapture capture, NotificationJob job, Object eventTypeObj) {
        io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> templateFlowable = buildTemplateGenerationFlowable(
                capture, job, eventTypeObj);

        List<MessageTemplate> newTemplates = messageTemplateRepository
                .findByEventType(capture.getEvent().getName());
        MessageTemplate newTemplate = newTemplates.stream()
                .filter(t -> t.getChannel().equalsIgnoreCase(job.getChannel()))
                .findFirst()
                .orElse(null);
        io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> scheduleFlowable = buildScheduleGenerationFlowable(
                capture, job, eventTypeObj, newTemplate);
        if (newTemplate != null) {
            job.setTemplate(newTemplate.getTemplate());
            return scheduleFlowable;
        }
        return agentOrchestrator.createSequentialFlowable(
                java.util.List.of(templateFlowable, scheduleFlowable));
    }

    private io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> buildTemplateGenerationFlowable(
            EventCapture capture, NotificationJob job, Object eventTypeObj) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Generate message templates for the following event:\n\n");
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("eventName", capture.getEvent().getName());
            payload.put("description", capture.getEvent().getDescription());
            payload.put("payload", capture.getPayload());
            payload.put("occuredAt", capture.getOccuredAt() != null ? capture.getOccuredAt().toString() : null);
            String jsonPayload = mapper.writeValueAsString(payload);
            Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));

            return agentOrchestrator.createTaskFlowable(
                    agentRegistry.get(AgentRegistry.MESSAGE_TEMPLATE_AGENT_ID),
                    UUID.randomUUID().toString(), prompt, AgentContextHolder.getContext())
                    .doOnNext(agentEvent -> {
                        if (agentEvent.content().isEmpty() || agentEvent.content().get().parts().isEmpty()
                                || agentEvent.content().get().parts().get().isEmpty()) {
                            return;
                        }
                        for (Part part : agentEvent.content().get().parts().get()) {
                            if (!part.text().isPresent())
                                continue;
                            String json = part.text().get();
                            List<Map<String, String>> templateList;
                            try {
                                templateList = mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                                });
                            } catch (Exception e) {
                                logger.warn("Failed to parse message templates from JSON");
                                continue;
                            }
                            for (Map<String, String> templateMap : templateList) {
                                MessageTemplate messageTemplate = new MessageTemplate();
                                messageTemplate.setChannel(templateMap.get("channel"));
                                messageTemplate.setSubject(templateMap.get("subject"));
                                messageTemplate.setTemplate(templateMap.get("template"));
                                messageTemplate.setEventName(capture.getEvent().getName());
                                messageTemplate.setEventType(capture.getEvent().getEventType());
                                messageTemplateRepository.save(messageTemplate);
                            }
                        }
                    });
        } catch (Exception e) {
            return io.reactivex.rxjava3.core.Flowable
                    .error(new AgentApplicationException("Failed to enqueue template generation", e));
        }
    }

    private io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> buildScheduleGenerationFlowable(
            EventCapture capture, NotificationJob job, Object eventTypeObj, MessageTemplate template) {

        List<EventSchedule> existingSchedules = eventScheduleRepository.findByEventName(capture.getEvent().getName());
        if (!existingSchedules.isEmpty()) {
            logger.info("Schedules already exist for event: {}. Bypassing agent generation.",
                    capture.getEvent().getName());
            for (EventSchedule existingSchedule : existingSchedules) {
                if (!existingSchedule.isValidated()) {
                    logger.warn("Reused EventSchedule for '{}' (ID: {}) requires validation",
                            capture.getEvent().getName(), existingSchedule.getId());
                } else {
                    job.setScheduleId(existingSchedule.getId());
                    notificationJobRepository.save(job);
                    capture.setStatus(CaptureStatus.DISPATCHED);
                    eventCaptureRepository.save(capture);
                    try {
                        notificationDispatcher.scheduleJob(existingSchedule);
                        logger.info("Scheduled reused EventSchedule for '{}' (ID: {})", capture.getEvent().getName(),
                                existingSchedule.getId());
                    } catch (Exception e) {
                        logger.error("Failed to schedule reused EventSchedule: {}", e.getMessage(), e);
                    }
                }

            }
            return io.reactivex.rxjava3.core.Flowable.empty();
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Generate notification schedules for the following events:\n\n");
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("eventName", capture.getEvent().getName());
            payload.put("eventType", eventTypeObj);
            payload.put("eventDescription", capture.getEvent().getDescription());
            payload.put("occuredAt", capture.getOccuredAt().toString());
            payload.put("scheduleIntent", capture.getEvent().getScheduleIntent());
            payload.put("preferredTimeWindow", capture.getEvent().getPreferredTimeWindow());
            String jsonPayload = mapper.writeValueAsString(payload);
            Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));

            return agentOrchestrator.createTaskFlowable(
                    agentRegistry.get(AgentRegistry.EVENT_SCHEDULER_AGENT_ID),
                    UUID.randomUUID().toString(), prompt, AgentContextHolder.getContext())
                    .doOnNext(agentEvent -> {
                        if (agentEvent.content().isEmpty() || agentEvent.content().get().parts().isEmpty()
                                || agentEvent.content().get().parts().get().isEmpty()) {
                            return;
                        }
                        for (Part part : agentEvent.content().get().parts().get()) {
                            if (!part.text().isPresent())
                                continue;
                            String json = part.text().get();
                            List<Map<String, String>> schedules;
                            try {
                                schedules = mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                                });
                            } catch (Exception e) {
                                logger.warn("Error parsing schedule JSON");
                                continue;
                            }

                            for (Map<String, String> scheduleMap : schedules) {
                                EventSchedule eventSchedule = new EventSchedule();
                                eventSchedule.setId(UUID.randomUUID().toString());
                                eventSchedule.setDescription(scheduleMap.get("scheduleDescription"));
                                eventSchedule.setEventName(capture.getEvent().getName());
                                eventSchedule.setTriggerType(scheduleMap.get("triggerType"));
                                if (scheduleMap.containsKey("triggerValue")
                                        && "cron".equals(scheduleMap.get("triggerType"))) {
                                    eventSchedule.setCronExpression(scheduleMap.get("triggerValue"));
                                }
                                eventSchedule.setScheduledAt(Instant.now());
                                eventScheduleRepository.save(eventSchedule);

                                job.setScheduleId(eventSchedule.getId());
                                notificationJobRepository.save(job);

                                capture.setStatus(CaptureStatus.DISPATCHED);
                                eventCaptureRepository.save(capture);

                                if (!eventSchedule.isValidated()) {
                                    logger.warn(
                                            "EventSchedule for '{}' (ID: {}) saved but not scheduled - requires validation",
                                            capture.getEvent().getName(), eventSchedule.getId());
                                } else {
                                    notificationDispatcher.scheduleJob(eventSchedule);
                                    logger.info("Scheduled validated EventSchedule for '{}' (ID: {})",
                                            capture.getEvent().getName(), eventSchedule.getId());
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            return io.reactivex.rxjava3.core.Flowable
                    .error(new AgentApplicationException("Failed to enqueue schedule generation", e));
        }
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
