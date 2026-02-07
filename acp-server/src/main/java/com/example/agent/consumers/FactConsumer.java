package com.example.agent.consumers;

import com.example.agent.AgentContextHolder;
import com.example.agent.AgentOrchestrator;
import com.example.agent.FactRepository;
import com.example.agent.config.AgentRegistry;
import com.example.agent.models.AgentContext;
import com.example.agent.models.FactEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FactConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final FactRepository factRepository;
    private Disposable disposable;

    @Value("${agent.buffer.timeout:15s}")
    private Duration bufferTimeout;

    public FactConsumer(AgentOrchestrator orchestrator, AgentRegistry agentRegistry, FactRepository factRepository) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
        this.factRepository = factRepository;
    }

    @PreDestroy
    public void onDestroy() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody Map<String, Object> request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "request_required"));
            }

            String clientId = (String) request.get("clientId");
            String sourceType = (String) request.getOrDefault("sourceType", "EVENT_LOG");
            String correlationId = (String) request.get("correlationId");

            @SuppressWarnings("unchecked")
            List<String> rawLogs = (List<String>) request.get("rawLogs");
            if (rawLogs == null) {
                String rawLog = (String) request.get("rawLog");
                rawLogs = rawLog != null ? List.of(rawLog) : List.of();
            }
            if (rawLogs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "rawLogs_required"));
            }

            // Prefer authenticated clientId if present
            AgentContext ctx = AgentContextHolder.getContext();
            String resolvedClientId = clientId;
            if (ctx.getSource() != null && !ctx.getSource().isBlank()) {
                resolvedClientId = ctx.getSource();
            }
            if (resolvedClientId == null || resolvedClientId.isBlank()) {
                resolvedClientId = "unknown";
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", resolvedClientId);
            payload.put("sourceType", sourceType);
            payload.put("rawLogs", rawLogs);
            if (correlationId != null)
                payload.put("correlationId", correlationId);

            String agentId = agentRegistry.get(AgentRegistry.LOG_TO_FACTS_AGENT_ID);
            if (agentId == null) {
                return ResponseEntity.internalServerError().body(Map.of("error", "agent_not_registered"));
            }

            Content prompt = Content.fromParts(
                    Part.fromText("Extract facts from the following raw logs."),
                    Part.fromText(mapper.writeValueAsString(payload)));

            final String finalClientId = resolvedClientId;
            disposable = orchestrator.executeTaskWithAgent(agentId, null, null, prompt)
                    .buffer(bufferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribe(events -> {
                        for (com.google.adk.events.Event agentEvent : events) {
                            if (agentEvent.content().isEmpty() || agentEvent.content().get().parts().isEmpty())
                                continue;
                            Optional<List<Part>> partsOpt = agentEvent.content().get().parts();
                            if (partsOpt.isEmpty())
                                continue;
                            for (Part part : partsOpt.get()) {
                                if (part.text().isEmpty())
                                    continue;
                                persistFactsFromJson(part.text().get(), finalClientId, sourceType, correlationId);
                            }
                        }
                    }, err -> {
                        // log
                        err.printStackTrace();
                    });

            return ResponseEntity.accepted().body(Map.of(
                    "status", "PROCESSING",
                    "message", "Fact extraction initiated",
                    "rawLogsCount", rawLogs.size()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private void persistFactsFromJson(String json, String clientId, String sourceType, String correlationId) {
        try {
            List<Map<String, Object>> facts = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> f : facts) {
                FactEntity entity = new FactEntity();
                entity.setClientId(clientId);
                entity.setSourceType(sourceType);
                entity.setFactType(asString(f.get("factType")));
                entity.setSentence(asString(f.get("sentence")));
                entity.setCorrelationId(asString(f.getOrDefault("correlationId", correlationId)));
                entity.setConfidence(asDouble(f.get("confidence"), 0.7));
                entity.setImportance(asDouble(f.get("importance"), 0.5));
                entity.setTtlDays(asInt(f.get("ttlDays"), 14));
                entity.setEvidenceJson(writeJson(f.get("evidence")));
                entity.setSourceEventIdsJson(writeJson(f.getOrDefault("sourceEventIds", List.of())));
                entity.setObservedAt(parseInstant(asString(f.get("observedAt"))));
                factRepository.save(entity);
            }
        } catch (Exception e) {
            // log parse failure
            e.printStackTrace();
        }
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
