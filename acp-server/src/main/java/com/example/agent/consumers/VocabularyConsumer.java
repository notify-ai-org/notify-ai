package com.example.agent.consumers;

import com.example.agent.EventRepository;
import com.example.agent.EventScheduleRepository;
import com.example.agent.MessageTemplateRepository;
import com.example.agent.RuleRepository;
import com.example.agent.AgentOrchestrator;
import com.example.agent.VocabularyRepository;
import com.example.agent.models.ClassModel;
import com.example.agent.models.Rule;
import com.example.agent.models.Vocabulary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyConsumer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VocabularyRepository repo;
    private final EventRepository eventRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final RuleRepository ruleRepository;
    private final AgentOrchestrator agentOrchestrator;
    private Disposable disposable;

    @PreDestroy
    public void onDestroy() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    // Configurable timeout or backpressure parameters
    @Value("${agent.buffer.timeout:15s}")
    private Duration bufferTimeout;

    public VocabularyConsumer(VocabularyRepository repo, AgentOrchestrator agentOrchestrator,
            EventRepository eventRepository, EventScheduleRepository eventScheduleRepository,
            MessageTemplateRepository messageTemplateRepository, RuleRepository ruleRepository) {
        this.repo = repo;
        this.agentOrchestrator = agentOrchestrator;
        this.eventRepository = eventRepository;
        this.eventScheduleRepository = eventScheduleRepository;
        this.messageTemplateRepository = messageTemplateRepository;
        this.ruleRepository = ruleRepository;
    }

    @KafkaListener(topics = "${vocab.kafka-topic}", groupId = "vocab-adk-group")
    @Transactional
    public void onMessage(String raw) {
        try {
            // Parse the raw message into a JSON map
            List<ClassModel> classes = mapper.readValue(raw, new TypeReference<List<ClassModel>>() {
            });
            processClasses(classes);
        } catch (Exception e) {
            // STEP 6️⃣: Let Kafka retry via Spring's error handler
            throw new RuntimeException("Failed processing vocabulary event", e);
        }
    }

    private void processClasses(List<ClassModel> classes) throws JsonProcessingException {
        // Check if vocabulary already exists
        List<Vocabulary> existing = repo.findByTermIgnoreCaseIn(
                classes.stream().map(ClassModel::getClassName)
                        .collect(Collectors.toList()));

        for (Vocabulary vocab : existing) {
            ClassModel classModel = classes.stream().filter(c -> c.getClassName().equalsIgnoreCase(vocab.getTerm()))
                    .findFirst().get();
            vocab.setDescription(classModel.getClassDescription());
            Vocabulary parent = repo.findByTermIgnoreCase(classModel.getSuperClass()).orElse(null);
            vocab.setParent(parent);
            vocab.setPath(parent == null ? vocab.getTerm() : parent.getPath() + "-" + vocab.getTerm());
            repo.save(vocab);
        }

        classes = classes.stream()
                .filter(c -> !existing.stream().anyMatch(e -> e.getTerm().equalsIgnoreCase(c.getClassName())))
                .collect(Collectors.toList());

        if (classes.size() == 0) {
            System.out.println("No new classes found in message");
            return;
        }

        // Process each class - save initial vocabulary terms
        for (ClassModel classModel : classes) {
            Vocabulary vocab = new Vocabulary();
            vocab.setTerm(classModel.getClassName());
            vocab.setDescription(classModel.getClassDescription());
            Vocabulary parent = repo.findByTermIgnoreCase(classModel.getSuperClass()).orElse(null);
            vocab.setParent(parent);
            vocab.setPath(parent == null ? vocab.getTerm() : parent.getPath() + "-" + vocab.getTerm());
            repo.save(vocab);
        }
    }

    /**
     * REST endpoint to create or update a vocabulary entry.
     * Accepts POST requests with vocabulary data and processes it similarly to
     * Kafka consumer.
     *
     * @param request The vocabulary request containing term, description, and path
     * @return ResponseEntity with success or error message
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createVocabulary(@RequestBody List<ClassModel> classes) {
        try {
            if (classes == null || classes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Classes are required", "status", "BAD_REQUEST"));
            }
            processClasses(classes);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Vocabulary processing initiated for " + classes.size() + " classes",
                    "status", "PROCESSING"));

        } catch (Exception e) {
            System.err.println("Error processing REST vocabulary request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "status", "ERROR"));
        }
    }

    /**
     * REST endpoint to process a rule definition using the RuleProcessorAgent.
     * Converts natural language rule descriptions to executable expressions.
     *
     * @param request The rule definition request containing eventName, ruleName, ruleDescription, and payload
     * @return ResponseEntity with status
     */
    @PostMapping("/rules/process")
    @Transactional
    public ResponseEntity<Map<String, Object>> processRule(@RequestBody Map<String, Object> request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Rule definition is required", "status", "BAD_REQUEST"));
            }

            String eventName = (String) request.get("eventName");
            String ruleName = (String) request.get("ruleName");
            String ruleDescription = (String) request.get("ruleDescription");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) request.get("payload");

            if (eventName == null || ruleName == null || ruleDescription == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "eventName, ruleName, and ruleDescription are required", "status", "BAD_REQUEST"));
            }

            processRuleWithAgent(eventName, ruleName, ruleDescription, payload)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribe(
                            events -> System.out.println("REST: Processed rule: " + ruleName),
                            error -> System.err.println("REST: Error processing rule: " + error.getMessage()));

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Rule processing initiated for: " + ruleName,
                    "status", "PROCESSING"));

        } catch (Exception e) {
            System.err.println("Error processing REST rule request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process rule request: " + e.getMessage(),
                            "status", "ERROR"));
        }
    }

    /**
     * Process a rule definition using the RuleProcessorAgent.
     * Converts natural language rule description to executable expression.
     *
     * @param eventName Name of the event this rule applies to
     * @param ruleName Name of the rule
     * @param ruleDescription Natural language description of the rule
     * @param payload Example payload structure (optional)
     * @return Flowable of agent events
     */
    private io.reactivex.rxjava3.core.Flowable<com.google.adk.events.Event> processRuleWithAgent(
            String eventName, String ruleName, String ruleDescription, Map<String, Object> payload) {
        try {
            Map<String, Object> ruleRequest = new HashMap<>();
            ruleRequest.put("eventName", eventName);
            ruleRequest.put("ruleName", ruleName);
            ruleRequest.put("ruleDescription", ruleDescription);
            if (payload != null) {
                ruleRequest.put("payload", payload);
            }

            String prompt = """
                Convert the following natural language rule definition to an executable expression
                using vocabulary terms from the database.
                """;

            return agentOrchestrator.executeTaskWithAgent(
                    "RuleProcessor",
                    null,
                    null,
                    Content.fromParts(Part.fromText(prompt), Part.fromText(mapper.writeValueAsString(ruleRequest)))
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
                                // Parse the rule expression result
                                Map<String, Object> ruleExpression = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                                
                                // Create and save the rule
                                Rule rule = new Rule();
                                rule.setId(UUID.randomUUID().toString());
                                rule.setName((String) ruleExpression.get("ruleName"));
                                rule.setEventName(eventName);
                                rule.setDescription(ruleDescription);
                                rule.setConditionExpr((String) ruleExpression.get("conditionExpr"));
                                rule.setEnabled(true);
                                rule.setPriority(0); // Default priority
                                
                                ruleRepository.save(rule);
                                System.out.println("Saved rule: " + rule.getName() + " with expression: " + rule.getConditionExpr());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing rule processor output: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).onErrorResumeNext(error -> {
                System.err.println("Rule processor agent call failed: " + error.getMessage());
                return io.reactivex.rxjava3.core.Flowable.empty();
            });
        } catch (Exception e) {
            System.err.println("Error invoking rule processor agent: " + e.getMessage());
            return io.reactivex.rxjava3.core.Flowable.error(e);
        }
    }
}
