package com.notify.agent.tools;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

import com.notify.agent.AgentContextHolder;
import com.notify.agent.EventRepository;
import com.notify.agent.RuleRepository;
import com.notify.agent.VocabularyRepository;
import com.notify.agent.models.AgentContext;
import com.notify.agent.models.EventCapture;
import com.notify.agent.models.Rule;
import com.notify.agent.models.Vocabulary;
import com.google.adk.tools.ToolContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ToolConfig {

    private static final Logger logger = Logger.getLogger(ToolConfig.class.getName());

    private final EventRepository eventRepository;
    private final RuleRepository ruleRepository;
    private final VocabularyRepository vocabularyRepository;

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

}
