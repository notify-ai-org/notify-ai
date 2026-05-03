package com.notify.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.agent.AgentContextHolder;
import com.notify.agent.EventRepository;
import com.notify.agent.RuleRepository;
import com.notify.agent.VocabularyRepository;
import com.notify.agent.models.AgentContext;
import com.notify.agent.models.DomainContentEntity;
import com.notify.agent.models.EventCapture;
import com.notify.agent.models.Rule;
import com.notify.agent.models.Vocabulary;
import com.notify.agent.service.DomainContentService;
import com.notify.agent.util.ObjectMapperFactory;
import com.google.adk.tools.ToolContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ToolConfig {

    private static final Logger logger = Logger.getLogger(ToolConfig.class.getName());

    private final EventRepository eventRepository;
    private final RuleRepository ruleRepository;
    private final VocabularyRepository vocabularyRepository;
    private final DomainContentService domainContentService;

    private static final ObjectMapper MAPPER = ObjectMapperFactory.create();

    @com.google.adk.tools.Annotations.Schema(name = "getEventHistory", description = """
                Get past emitted events, use the success,error frequency and occurence time of events to decide current one has to be emitted or not
            """)
    public Map<String, Object> getEventHistory(
            @com.google.adk.tools.Annotations.Schema(name = "eventName", description = "The name of the event under consideration") String eventName,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting event history for event: %s (Call ID: %s)",
                eventName, toolContext.functionCallId().orElse("N/A")));
        // Query events by type/name
        List<EventCapture> events = eventRepository.getHistory(eventName);
        return Map.of("events", events);
    }

    @com.google.adk.tools.Annotations.Schema(name = "getRulesForEvent", description = """
                Get past emitted events, use the success,error frequency and occurence time of events to decide current one has to be emitted or not
            """)
    public Map<String, Object> getRulesForEvent(
            @com.google.adk.tools.Annotations.Schema(name = "eventName", description = "The name of the event under consideration") String eventName,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting rules for event: %s (Call ID: %s)",
                eventName, toolContext.functionCallId().orElse("N/A")));
        // Query rules by event name
        List<Rule> rules = ruleRepository.findByEventName(eventName);
        return Map.of("rules", rules);
    }

    @com.google.adk.tools.Annotations.Schema(name = "getDomainContextKnowledge", description = """
                Get domain context knowledge for the event
            """)
    public Map<String, Object> getDomainContextKnowledge(
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting domain context knowledge (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));
        // Query domain context knowledge
        AgentContext agentContext = AgentContextHolder.getContext();
        return Map.of("knowledge", agentContext.getContent().text());
    }

    @com.google.adk.tools.Annotations.Schema(name = "searchVocabulary", description = """
                Search for vocabulary terms by term name or description. Use this to find the correct vocabulary terms
                to use in rule expressions. Returns vocabulary entries with their terms, descriptions, and paths.
            """)
    public Map<String, Object> searchVocabulary(
            @com.google.adk.tools.Annotations.Schema(name = "terms", description = "List of term names to search for") List<String> terms,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Searching vocabulary for terms: %s (Call ID: %s)",
                terms, toolContext.functionCallId().orElse("N/A")));
        if (terms == null || terms.isEmpty()) {
            return Map.of("vocabularies", List.of());
        }
        List<Vocabulary> vocabularies = vocabularyRepository.findByTermIgnoreCaseIn(terms);
        return Map.of("vocabularies", vocabularies);
    }

    @com.google.adk.tools.Annotations.Schema(name = "getExistingRules", description = """
                Get existing rules for reference. Use this to understand the format and structure of rule expressions.
            """)
    public Map<String, Object> getExistingRules(
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting existing rules for reference (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));
        List<Rule> rules = ruleRepository.findAll();
        return Map.of("rules", rules);
    }

    @com.google.adk.tools.Annotations.Schema(name = "listAllVocabulary", description = """
                Get a list of all available vocabulary terms and their descriptions.
                Use this to discover which terms can be used in rule expressions.
            """)
    public Map<String, Object> listAllVocabulary(
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Listing all vocabulary (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));
        List<Vocabulary> vocabularies = vocabularyRepository.findAll();
        return Map.of("vocabularies", vocabularies);
    }

    @com.google.adk.tools.Annotations.Schema(name = "getDomainContentKeys", description = """
                Returns all content keys and their descriptions available from the
                domain content service for the current client/tenant.
                Call this before generating an EMAIL template so you know which
                ${KEY} placeholders are available (e.g. LOGO_SMALL, BRAND_BG_COLOR, UNSUBSCRIBE_URL).
                Use these keys as ${KEY} placeholders ONLY inside EMAIL HTML templates.
            """)
    public Map<String, Object> getDomainContentKeys(
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Fetching domain content keys (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));

        AgentContext agentContext = AgentContextHolder.getContext();
        String clientId = (agentContext != null && agentContext.getSource() != null)
                ? agentContext.getSource()
                : null;

        if (clientId == null) {
            logger.log(Level.WARNING, "No clientId found in AgentContext; returning empty content keys");
            return Map.of("keys", List.of());
        }

        // Prefer FULL type; fall back to VOCABULARY if none found
        List<DomainContentEntity> entities = domainContentService.findByClientIdAndType(
                clientId, DomainContentEntity.Type.FULL);
        if (entities == null || entities.isEmpty()) {
            entities = domainContentService.findByClientIdAndType(
                    clientId, DomainContentEntity.Type.VOCABULARY);
        }

        List<Map<String, String>> keys = new ArrayList<>();
        if (entities != null) {
            for (DomainContentEntity entity : entities) {
                String keyName = entity.getKeyName();
                if (keyName == null || keyName.isBlank()) continue;
                String description = entity.getDescription() != null ? entity.getDescription() : "";
                // Surface the stored value as a hint if it's already populated
                String value = entity.getContent() != null ? entity.getContent() : "";
                Map<String, String> entry = new java.util.LinkedHashMap<>();
                entry.put("key", keyName);
                entry.put("description", description);
                if (!value.isBlank()) entry.put("currentValue", value);
                keys.add(entry);
            }
        }

        return Map.of("keys", keys);
    }

    @com.google.adk.tools.Annotations.Schema(name = "saveMissingContentKeys", description = """
                After generating an EMAIL HTML template, call this tool to persist any
                ${KEY} placeholders you used that were NOT returned by getDomainContentKeys.
                Each entry will be saved as a stub in the domain content table so that
                an administrator can later supply the actual values.
                Pass only keys that are genuinely missing — do not re-save existing ones.
            """)
    public Map<String, Object> saveMissingContentKeys(
            @com.google.adk.tools.Annotations.Schema(name = "missingKeys", description = "List of objects, each with 'key' (the placeholder name, e.g. LOGO_SMALL) and 'description' (what value is expected).") List<Map<String, String>> missingKeys,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Saving %d missing content keys (Call ID: %s)",
                missingKeys == null ? 0 : missingKeys.size(),
                toolContext.functionCallId().orElse("N/A")));

        if (missingKeys == null || missingKeys.isEmpty()) {
            return Map.of("saved", 0, "message", "No missing keys provided.");
        }

        AgentContext agentContext = AgentContextHolder.getContext();
        String clientId = (agentContext != null && agentContext.getSource() != null)
                ? agentContext.getSource()
                : null;

        if (clientId == null) {
            logger.log(Level.WARNING, "No clientId found in AgentContext; cannot save missing content keys.");
            return Map.of("saved", 0, "message", "No clientId found in context.");
        }
        List<DomainContentEntity> newEntities = new ArrayList<>();
        // Load or create the FULL content blob
        try {
            newEntities = domainContentService.upsert(clientId, missingKeys);
            logger.log(Level.INFO,
                    "Saved " + newEntities.size() + " missing content key stubs for client: " + clientId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to persist missing content keys", e);
            return Map.of("saved", 0, "message", "Persistence error: " + e.getMessage());
        }

        return Map.of("saved", newEntities.size(),
                "message", newEntities.size() + " new key stub(s) saved. Existing keys were not overwritten.");
    }

    @com.google.adk.tools.Annotations.Schema(name = "getVocabularyForTemplate", description = """
                Fetches vocabulary terms by name and resolves each one to its full dot-path
                (e.g. the term "orderId" whose parent is "payload" resolves to "payload.orderId").
                Returns a list of { term, path, description, type } objects.
                Use the resolved "path" value as the placeholder inside template strings,
                wrapped in double curly braces: {{path}} (e.g. {{payload.orderId}}).
                Call this for ALL channels — EMAIL, SMS, PUSH, IN_APP — before writing any template.
            """)
    public Map<String, Object> getVocabularyForTemplate(
            @com.google.adk.tools.Annotations.Schema(name = "terms", description = "List of vocabulary term names relevant to the event being templated (e.g. [\"orderId\", \"orderAmount\", \"customerName\"]).") List<String> terms,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Resolving vocabulary paths for terms: %s (Call ID: %s)",
                terms, toolContext.functionCallId().orElse("N/A")));

        if (terms == null || terms.isEmpty()) {
            return Map.of("vocabularies", List.of());
        }

        List<Vocabulary> found = vocabularyRepository.findByTermIgnoreCaseIn(terms);

        // Load all vocab entries to resolve parent chains
        List<Vocabulary> all = vocabularyRepository.findAll();
        Map<Long, Vocabulary> byId = new java.util.HashMap<>();
        for (Vocabulary v : all) {
            if (v.getId() != null)
                byId.put(v.getId(), v);
        }

        List<Map<String, String>> resolved = new ArrayList<>();
        for (Vocabulary v : found) {
            String path = resolvePath(v, byId);
            Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("term", v.getTerm());
            entry.put("path", path);
            entry.put("description", v.getDescription() != null ? v.getDescription() : "");
            entry.put("type", v.getType() != null ? v.getType() : "");
            resolved.add(entry);
        }

        return Map.of("vocabularies", resolved);
    }

    /**
     * Walks the parent chain of a Vocabulary node and builds a dot-separated path.
     * e.g. term "orderId" with parent "payload" → "payload.orderId"
     */
    private String resolvePath(Vocabulary node, Map<Long, Vocabulary> byId) {
        java.util.Deque<String> segments = new java.util.ArrayDeque<>();
        Vocabulary current = node;
        int guard = 0;
        while (current != null && guard++ < 16) {
            segments.addFirst(current.getTerm());
            Vocabulary parent = current.getParent();
            if (parent == null || parent.getId() == null)
                break;
            current = byId.get(parent.getId());
        }
        return String.join(".", segments);
    }

}
