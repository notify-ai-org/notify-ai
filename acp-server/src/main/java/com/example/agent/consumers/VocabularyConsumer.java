package com.example.agent.consumers;

import com.example.agent.RuleRepository;
import com.example.agent.AgentOrchestrator;
import com.example.agent.VocabularyRepository;
import com.example.agent.models.ClassModel;
import com.example.agent.models.Rule;
import com.example.agent.models.Vocabulary;
import com.example.agent.util.ObjectMapperFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.example.agent.exceptions.AgentApplicationException;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import com.example.agent.annotations.ManagedConfiguration;
import com.example.agent.annotations.ManagedConfiguration.ConfigSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyConsumer {
    private final ObjectMapper mapper = ObjectMapperFactory.create();
    private final VocabularyRepository repo;
    private final RuleRepository ruleRepository;
    private final AgentOrchestrator agentOrchestrator;

    // Configurable timeout or backpressure parameters
    @Value("${agent.buffer.timeout:15s}")
    @ManagedConfiguration(key = "agent.buffer.timeout", source = ConfigSource.CONFIG_MAP)
    private Duration bufferTimeout;

    public VocabularyConsumer(VocabularyRepository repo, AgentOrchestrator agentOrchestrator,
            RuleRepository ruleRepository) {
        this.repo = repo;
        this.agentOrchestrator = agentOrchestrator;
        this.ruleRepository = ruleRepository;
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
            repo.save(vocab);
        }
    }

    /**
     * REST endpoint to create or update a vocabulary entry.
     */
    @PostMapping
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> createVocabulary(@RequestBody List<ClassModel> classes) {
        if (classes == null || classes.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Classes are required", "status", "BAD_REQUEST")));
        }
        try {
            processClasses(classes);
            return Mono.just(ResponseEntity.accepted().body(Map.of(
                    "message", "Vocabulary processing initiated for " + classes.size() + " classes",
                    "status", "PROCESSING")));
        } catch (Exception e) {
            throw new AgentApplicationException("Error creating vocabulary", e);
        }
    }

    /**
     * REST endpoint to process a rule definition using the RuleProcessorAgent.
     * Enqueues the rule processing task and returns immediately.
     */
    @PostMapping("/rules/process")
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> processRule(@RequestBody Map<String, Object> request) {
        if (request == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Rule definition is required", "status", "BAD_REQUEST")));
        }

        String eventName = (String) request.get("eventName");
        String ruleName = (String) request.get("ruleName");
        String ruleDescription = (String) request.get("ruleDescription");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        if (eventName == null || ruleName == null || ruleDescription == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "eventName, ruleName, and ruleDescription are required", "status",
                            "BAD_REQUEST")));
        }

        processRuleWithAgent(eventName, ruleName, ruleDescription, payload);

        return Mono.just(ResponseEntity.accepted().body(Map.of(
                "message", "Rule processing enqueued for: " + ruleName,
                "status", "ENQUEUED")));
    }

    /**
     * Enqueue rule processing with the RuleProcessorAgent.
     * The callback persists the resulting rule when the agent completes.
     */
    private void processRuleWithAgent(
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

            Content content = Content.fromParts(
                    Part.fromText(prompt),
                    Part.fromText(mapper.writeValueAsString(ruleRequest)));

            agentOrchestrator.executeTaskWithAgent("RuleProcessor", null, content, flowable -> {
                flowable.subscribe(agentEvent -> {
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
                                    Map<String, Object> ruleExpression = mapper.readValue(json,
                                            new TypeReference<Map<String, Object>>() {
                                            });

                                    Rule rule = new Rule();
                                    rule.setId(UUID.randomUUID().toString());
                                    rule.setName((String) ruleExpression.get("ruleName"));
                                    rule.setEventName(eventName);
                                    rule.setDescription(ruleDescription);
                                    rule.setConditionExpr((String) ruleExpression.get("conditionExpr"));
                                    rule.setEnabled(true);
                                    rule.setPriority(0);

                                    ruleRepository.save(rule);
                                    System.out.println("Saved rule: " + rule.getName() + " with expression: "
                                            + rule.getConditionExpr());
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing rule processor output: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }, error -> {
                    System.err.println("Rule processor agent call failed: " + error.getMessage());
                });
            });
        } catch (Exception e) {
            System.err.println("Error invoking rule processor agent: " + e.getMessage());
            throw new AgentApplicationException("Error invoking rule processor agent", e);
        }
    }
}
