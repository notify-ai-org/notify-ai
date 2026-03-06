package com.example.agent.consumers;

import com.example.agent.AgentOrchestrator;
import com.example.agent.FactRepository;
import com.example.agent.config.AgentRegistry;
import com.example.agent.interfaces.MemoryAssembler;
import com.example.agent.models.FactEntity;
import com.example.agent.models.RawLog;
import com.example.agent.records.Fact;
import com.example.agent.util.ObjectMapperFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.agent.annotations.ManagedConfiguration;
import com.example.agent.annotations.ManagedConfiguration.ConfigSource;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class FactConsumer {

    private final ObjectMapper mapper = ObjectMapperFactory.create();
    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final FactRepository factRepository;
    private final MemoryAssembler pageAssembler;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Value("${agent.buffer.timeout:15s}")
    @ManagedConfiguration(key = "agent.buffer.timeout", source = ConfigSource.CONFIG_MAP)
    private Duration bufferTimeout;

    public FactConsumer(AgentOrchestrator orchestrator, AgentRegistry agentRegistry,
            FactRepository factRepository, MemoryAssembler pageAssembler) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
        this.factRepository = factRepository;
        this.pageAssembler = pageAssembler;
    }

    @PreDestroy
    public void onDestroy() {
        compositeDisposable.dispose();
    }

    /**
     * Primary entry point for extracting facts from a batch of raw logs.
     * Feeds the logs into the Log-to-Facts agent and persists resulting facts.
     */
    public void extractFacts(List<? extends RawLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        // Extract context from the first log
        RawLog exemplar = logs.get(0);
        String tenantId = exemplar.getTenantId() != null ? exemplar.getTenantId() : "unknown";
        String correlationId = exemplar.getCorrelationId();
        String sourceType = determineSourceType(logs);

        try {
            String agentId = agentRegistry.get(AgentRegistry.LOG_TO_FACTS_AGENT_ID);
            if (agentId == null) {
                throw new IllegalStateException("extraction_agent_not_found");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceType", sourceType);
            payload.put("logs", logs);
            if (correlationId != null) {
                payload.put("correlationId", correlationId);
            }

            Content prompt = Content.fromParts(
                    Part.fromText("Extract facts from the following raw logs."),
                    Part.fromText(mapper.writeValueAsString(payload)));

            Disposable d = orchestrator.executeTaskWithAgent(agentId, null, null, prompt)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribe(events -> {
                        for (com.google.adk.events.Event agentEvent : events) {
                            agentEvent.content().flatMap(Content::parts).ifPresent(parts -> {
                                for (Part part : parts) {
                                    part.text().ifPresent(json -> {
                                        List<Fact> facts = persistFactsFromJson(json, tenantId, sourceType,
                                                correlationId);
                                        if (!facts.isEmpty()) {
                                            pageAssembler.buildPages(facts);
                                        }
                                    });
                                }
                            });
                        }
                    }, Throwable::printStackTrace);

            compositeDisposable.add(d);

        } catch (Exception e) {
            throw new RuntimeException("Fact extraction initiation failed", e);
        }
    }

    private String determineSourceType(List<? extends RawLog> logs) {
        if (logs == null || logs.isEmpty())
            return "RAW_LOG";
        String className = logs.get(0).getClass().getSimpleName();
        return switch (className) {
            case "EventCapture" -> "EVENT";
            case "EventExecutionLog" -> "EXECUTION";
            case "NotificationAttemptLog" -> "NOTIFICATION";
            default -> "RAW_LOG";
        };
    }

    private List<Fact> persistFactsFromJson(String json, String tenantId, String sourceType, String correlationId) {
        List<Fact> result = new ArrayList<>();
        try {
            List<Map<String, Object>> facts = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> f : facts) {
                String factType = asString(f.get("factType"));
                String sentence = asString(f.get("sentence"));
                Instant observedAt = parseInstant(asString(f.get("observedAt")));
                String factCorrelationId = asString(f.getOrDefault("correlationId", correlationId));

                FactEntity entity = new FactEntity();
                entity.setClientId(tenantId);
                entity.setSourceType(sourceType);
                entity.setFactType(factType);
                entity.setSentence(sentence);
                entity.setCorrelationId(factCorrelationId);
                entity.setConfidence(asDouble(f.get("confidence"), 0.7));
                entity.setImportance(asDouble(f.get("importance"), 0.5));
                entity.setTtlDays(asInt(f.get("ttlDays"), 14));
                entity.setEvidenceJson(writeJson(f.get("evidence")));
                entity.setSourceEventIdsJson(writeJson(f.getOrDefault("sourceEventIds", List.of())));
                entity.setObservedAt(observedAt);
                factRepository.save(entity);

                @SuppressWarnings("unchecked")
                List<String> sourceEventIds = (List<String>) f.getOrDefault("sourceEventIds", List.of());

                result.add(new Fact(
                        null, // factId generated later or not needed for assembly
                        factType,
                        sentence,
                        observedAt,
                        entity.getConfidence(),
                        entity.getImportance(),
                        entity.getTtlDays(),
                        sourceEventIds,
                        factCorrelationId));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private double asDouble(Object o, double def) {
        if (o == null)
            return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private int asInt(Object o, int def) {
        if (o == null)
            return def;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private String writeJson(Object o) {
        if (o == null)
            return null;
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank())
            return Instant.now();
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
