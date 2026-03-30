package com.example.agent.consumers;

import com.example.agent.RuleRepository;
import com.example.agent.AgentOrchestrator;
import com.example.agent.VocabularyRepository;
import com.example.agent.models.AttributeModel;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(VocabularyConsumer.class);

    public VocabularyConsumer(VocabularyRepository repo, AgentOrchestrator agentOrchestrator,
            RuleRepository ruleRepository) {
        this.repo = repo;
        this.agentOrchestrator = agentOrchestrator;
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public void processClasses(List<ClassModel> classes) throws JsonProcessingException {
        // Prefetch all existing vocabulary into memory as a performance cache
        List<Vocabulary> allVocabEntries = repo.findAll();

        // Separate into root (class) and child (attribute) caches
        Map<String, Vocabulary> classCache = new HashMap<>();
        Map<String, Vocabulary> attributeCacheByParent = new HashMap<>();

        for (Vocabulary vocab : allVocabEntries) {
            if (vocab.getParent() == null) {
                classCache.put(vocab.getTerm().toLowerCase(), vocab);
            } else {
                String key = vocab.getParent().getId() + ":" + vocab.getTerm().toLowerCase();
                attributeCacheByParent.put(key, vocab);
            }
        }

        for (ClassModel classModel : classes) {
            // 1. Get or create the class vocabulary — DB lookup is authoritative
            String classNameLower = classModel.getClassName().toLowerCase();
            Vocabulary classVocab = classCache.computeIfAbsent(classNameLower,
                    k -> repo.findByTermIgnoreCaseAndParent(classModel.getClassName(), null)
                            .orElseGet(() -> {
                                Vocabulary v = new Vocabulary();
                                v.setTerm(classModel.getClassName());
                                return v;
                            }));

            classVocab.setDescription(classModel.getClassDescription());

            // Super class lookup (cache-first, then DB)
            if (classModel.getSuperClass() != null) {
                String superClassLower = classModel.getSuperClass().toLowerCase();
                Vocabulary parentClassVocab = classCache.computeIfAbsent(superClassLower,
                        k -> repo.findByTermIgnoreCaseAndParent(classModel.getSuperClass(), null)
                                .orElse(null));
                classVocab.setParent(parentClassVocab);
            }

            classVocab = repo.saveAndFlush(classVocab);  // flush so parent is visible to attribute SELECT
            // Refresh the cache with the now-persisted instance (has a DB id)
            classCache.put(classNameLower, classVocab);

            // 2. Process attributes — DB lookup guards against duplicate key violations
            if (classModel.getAttributes() != null) {
                final Vocabulary savedClassVocab = classVocab;
                for (AttributeModel attr : classModel.getAttributes()) {
                    String attrKey = savedClassVocab.getId() + ":" + attr.getName().toLowerCase();

                    Vocabulary attrVocab = attributeCacheByParent.computeIfAbsent(attrKey,
                            k -> repo.findByTermIgnoreCaseAndParent(attr.getName(), savedClassVocab)
                                    .orElseGet(() -> {
                                        Vocabulary v = new Vocabulary();
                                        v.setTerm(attr.getName());
                                        v.setParent(savedClassVocab);
                                        return v;
                                    }));

                    attrVocab.setDescription(attr.getDescription());
                    attrVocab.setType(attr.getType());
                    Vocabulary saved = repo.save(attrVocab);
                    attributeCacheByParent.put(attrKey, saved);
                }
            }
        }
    }

    /**
     * REST endpoint to create or update a vocabulary entry.
     */
    @PostMapping
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

            String taskId = UUID.randomUUID().toString();
            com.example.agent.models.AgentContext context = com.example.agent.AgentContextHolder.getContext();

            agentOrchestrator.createTaskFlowable("RuleProcessor", taskId, content, context)
                    .subscribe(agentEvent -> {
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
                                        try {
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
                                        } catch (Exception e) {
                                            logger.error("Error parsing schedule JSON");
                                            continue;
                                        }
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
        } catch (Exception e) {
            System.err.println("Error invoking rule processor agent: " + e.getMessage());
            throw new AgentApplicationException("Error invoking rule processor agent", e);
        }
    }
}
