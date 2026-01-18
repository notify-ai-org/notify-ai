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
import com.example.agent.models.AgentContext;
import com.example.agent.models.EventCapture;
import com.example.agent.models.EventSchedule;
import com.example.agent.models.MessageTemplate;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.NotificationJob.NotificationPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
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

@Component
@RestController
@RequestMapping("/api/event")
public class EventConsumer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventRepository repo;
    private final EventCaptureRepository eventCaptureRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationJobRepository notificationJobRepository;
    private Disposable disposable;

    // Configurable timeout or backpressure parameters
    @Value("${agent.buffer.timeout:15s}")
    private Duration bufferTimeout;

    public EventConsumer(EventRepository repo, EventCaptureRepository eventCaptureRepository,
            EventScheduleRepository eventScheduleRepository, MessageTemplateRepository messageTemplateRepository
            ,AgentOrchestrator agentOrchestrator,
            NotificationDispatcher notificationDispatcher,NotificationJobRepository notificationJobRepository) {
        this.repo = repo;
        this.eventCaptureRepository = eventCaptureRepository;
        this.eventScheduleRepository = eventScheduleRepository;
        this.messageTemplateRepository = messageTemplateRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.notificationDispatcher = notificationDispatcher;
        this.notificationJobRepository = notificationJobRepository;
    }

    @KafkaListener(topics = "${vocab.kafka-topic}", groupId = "vocab-adk-group")
    @Transactional
    public void onMessage(String raw) {
        try {
            List<EventCapture> captures = mapper.readValue(raw, new TypeReference<List<EventCapture>>() {});
            processEventsWithAgent(captures)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
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
     * @return ResponseEntity with status
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> processEvents(@RequestBody List<EventCapture> request) {
        try {
            if (request == null || request.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Event captures are required", "status", "BAD_REQUEST"));
            }

            processEventsWithAgent(request)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribe(
                            events -> System.out
                                    .println("REST: Processed " + events.size() + " event selection results"),
                            error -> System.err.println("REST: Error processing events: " + error.getMessage()));

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Event processing initiated for " + request.size() + " captures",
                    "status", "PROCESSING"));

        } catch (Exception e) {
            System.err.println("Error processing REST event request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process event request: " + e.getMessage(),
                            "status", "ERROR"));
        }
    }


    private Flowable<com.google.adk.events.Event> processEventsWithAgent(List<EventCapture> captures) {
        try {
            List<Flowable<com.google.adk.events.Event>> flows = new java.util.ArrayList<>();
            for (EventCapture capture : captures) {
                if (capture == null || capture.getEventName() == null) {
                    throw new RuntimeException("Event capture is required with an eventName");
                }
                if (capture.getEventType() == null) {
                    throw new RuntimeException("Event capture is required with an eventType");
                }
                if (capture.getOccuredAt() == null) {
                    throw new RuntimeException("Event capture is required with an occuredAt");
                }
                if (capture.getPayload() == null) {
                    throw new RuntimeException("Event capture is required with a payload");
                }

                String prompt = """
                    Analyze the event and give the output as instructed
                    """;

                Flowable<com.google.adk.events.Event> flow = agentOrchestrator.executeTaskWithAgent(
                   "EventProcessorAgent",
                   null,
                   null,
                   Content.fromParts(Part.fromText(prompt), Part.fromText(mapper.writeValueAsString(capture)))
                ).doOnNext(agentEvent -> {
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
                                    List<Map<String, Object>> processedEvents = mapper.readValue(json,new TypeReference<List<Map<String, Object>>>() {});
                                    for (Map<String, Object> processedEvent : processedEvents) {
                                       Object eventTypeObj = processedEvent.get("eventType");
                                        // Extract ruleExpressions from event level
                                        @SuppressWarnings("unchecked")
                                        List<String> ruleExpressionsList = (List<String>) processedEvent.get("ruleExpressions");
                                        String ruleExpressions = ruleExpressionsList != null 
                                            ? String.join(",", ruleExpressionsList) 
                                            : null;
                                        
                                        // Extract channels and create notification jobs
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, String>> channels = (List<Map<String, String>>) processedEvent.get("channels");
                                        if (channels != null) {
                                            for (Map<String, String> channelInfo : channels) {
                                                String channel = channelInfo.get("channel");
                                                String subject = channelInfo.get("subject");
                                                
                                                // Find template for this event and channel
                                                List<MessageTemplate> templates = messageTemplateRepository.findByEventType(capture.getEventName());

                                                MessageTemplate template = templates.stream()
                                                        .filter(t -> t.getChannel()
                                                                .equalsIgnoreCase(channel))
                                                        .findFirst()
                                                        .orElse(null);
                                                
                                                AgentContext agentContext = AgentContextHolder.getContext();
                                                // Create notification job
                                                NotificationJob.NotificationJobBuilder jobBuilder = NotificationJob.builder()
                                                    .id(UUID.randomUUID().toString())
                                                    .channel(channel)
                                                    .target(subject)
                                                    .idempotencyKey(agentContext.getIdempotencyKey())
                                                    .schemaVersion(agentContext.getSchemaVersion())
                                                    .source(agentContext.getSource())
                                                    .correlationId(agentContext.getCorrelationId())
                                                    .eventType(capture.getEventType())
                                                    .priority(NotificationPriority.valueOf(processedEvent.get("priority").toString()))
                                                    .dispatchMode(NotificationJob.DispatchMode.EVENT)
                                                    .retryCount(0)
                                                    .maxRetries(3)
                                                    .createdAt(java.time.Instant.now())
                                                    .updatedAt(java.time.Instant.now());
                                                

                                                if (template != null) {
                                                    jobBuilder.template(template.getTemplate());
                                                } else {
                                                    throw new Exception("Cannot find template for given job");
                                                }
                                                jobBuilder.ruleExpressions(ruleExpressions);

                                                NotificationJob job = jobBuilder.build();
                                                if (eventTypeObj != null && eventTypeObj.equals("static")) {
                                                    notificationDispatcher.pushJob(job); 
                                                    notificationJobRepository.save(job);
                                                } else {
                                                    generateTemplatesAndSchedules(capture,job);
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

    private Disposable generateTemplatesAndSchedules(EventCapture capture,NotificationJob job){
        try {
            List<AgentTaskContext> tasks = new ArrayList<>();
            // Generate schedules and templates using agents
            AgentTaskContext scheduleCtx = generateScheduleWithAgent(capture);
            tasks.add(scheduleCtx);
            // Find template for this event and channel
            List<MessageTemplate> templates = messageTemplateRepository.findByEventType(capture.getEventName());
            if(templates.isEmpty()){
                if(capture.getEventType().equals("deferred")){
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
                                                List<Map<String, String>> schedules = mapper.readValue(json,new TypeReference<List<Map<String, String>>>() {});

                                                for (Map<String, String> scheduleMap : schedules) {
                                                    EventSchedule eventSchedule = new EventSchedule();
                                                    eventSchedule.setId(UUID.randomUUID().toString());
                                                    eventSchedule.setDescription(scheduleMap.get("scheduleDescription"));
                                                    eventSchedule.setEventName(capture.getEventName());
                                                    if (scheduleMap.containsKey("triggerValue")
                                                            && scheduleMap.get("triggerType")
                                                                    .equals("cron")) {
                                                        eventSchedule.setCronExpression(
                                                                scheduleMap.get("triggerValue"));
                                                    }
                                                    eventSchedule.setScheduledAt(Instant.now());
                                                    eventScheduleRepository.save(eventSchedule);

                                                    // dispatch deferred event schedule
                                                    notificationDispatcher.scheduleJob(eventSchedule);
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
                                                List<Map<String, String>> templateList = mapper.readValue(json,new TypeReference<List<Map<String, String>>>() {});

                                                for (Map<String, String> templateMap : templateList) {
                                                    MessageTemplate messageTemplate = new MessageTemplate();
                                                    messageTemplate.setChannel(templateMap.get("channel"));
                                                    messageTemplate.setSubject(templateMap.get("subject"));
                                                    messageTemplate.setTemplate(templateMap.get("template"));
                                                    messageTemplate.setEventName(capture.getEventName());
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
                },error -> System.err.println("Agent call failed: " + error.getMessage()));
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
        payload.put("eventName", capture.getEventName());
        payload.put("eventType", capture.getEventType());
        payload.put("payload", capture.getPayload());
        payload.put("eventDescription", capture.getEventDescription());
        payload.put("occuredAt", capture.getOccuredAt().toString());
        payload.put("scheduleIntent", capture.getScheduleIntent());
        payload.put("preferredTimeWindow", capture.getPreferredTimeWindow());
        payload.put("occuredAt", capture.getOccuredAt() != null ? capture.getOccuredAt().toString() : null);
        payload.put("durationMillis", capture.getDurationMillis());
        payload.put("serviceName", capture.getServiceName());
        String jsonPayload = mapper.writeValueAsString(payload);
        Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));
        return agentOrchestrator.new AgentTaskContext("EventNotificationScheduler", null, null, prompt);
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
        payload.put("eventName", capture.getEventName());
        payload.put("eventType", capture.getEventType());
        payload.put("eventDescription", capture.getEventDescription());
        payload.put("payload", capture.getPayload());
        payload.put("occuredAt", capture.getOccuredAt() != null ? capture.getOccuredAt().toString() : null);

        // add user instruction prompts

        String jsonPayload = mapper.writeValueAsString(payload);
        Content prompt = Content.fromParts(Part.fromText(sb.toString()), Part.fromText(jsonPayload));
        return agentOrchestrator.new AgentTaskContext("MessageTemplateGenerator", null, null, prompt);
    }
}
