package com.example.agent.tools;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

import com.example.agent.AgentContextHolder;
import com.example.agent.EventRepository;
import com.example.agent.RuleRepository;
import com.example.agent.VocabularyRepository;
import com.example.agent.models.AgentContext;
import com.example.agent.models.EventCapture;
import com.example.agent.models.Rule;
import com.example.agent.models.Vocabulary;
import com.google.adk.tools.ToolContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ToolConfig {

    private static final Logger logger = Logger.getLogger(ToolConfig.class.getName());

    private EventRepository eventRepository;

    private RuleRepository ruleRepository;

    private VocabularyRepository vocabularyRepository;

    @com.google.adk.tools.Annotations.Schema(name = "getEventHistory", description = """
        Get past emitted events, use the success,error frequency and occurence time of events to decide current one has to be emitted or not
    """)
    public List<EventCapture> getEventHistory(
        @com.google.adk.tools.Annotations.Schema(name = "eventName", description = "The name of the event under consideration") String eventName,
        @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
        ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting event history for event: %s (Call ID: %s)",
                eventName, toolContext.functionCallId().orElse("N/A")));
        // Query events by type/name
        List<EventCapture> events = eventRepository.getHistory(eventName);
        return events;
    }

    @com.google.adk.tools.Annotations.Schema(name = "getRulesForEvent", description = """
            Get past emitted events, use the success,error frequency and occurence time of events to decide current one has to be emitted or not
        """)
    public List<Rule> getRulesForEvent(
        @com.google.adk.tools.Annotations.Schema(name = "eventName", description = "The name of the event under consideration") String eventName,
        @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
        ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting rules for event: %s (Call ID: %s)",
                eventName, toolContext.functionCallId().orElse("N/A")));
        // Query rules by event name
        List<Rule> rules = ruleRepository.findByEventName(eventName);
        return rules;
    }

    @com.google.adk.tools.Annotations.Schema(name = "getDomainContextKnowledge", description = """
        Get domain context knowledge for the event
    """)
    public String getDomainContextKnowledge(
        @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
        ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting domain context knowledge (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));
        // Query domain context knowledge
        AgentContext agentContext = AgentContextHolder.getContext();
        return agentContext.getContent().text();
    }

    @com.google.adk.tools.Annotations.Schema(name = "searchVocabulary", description = """
        Search for vocabulary terms by term name or description. Use this to find the correct vocabulary terms
        to use in rule expressions. Returns vocabulary entries with their terms, descriptions, and paths.
    """)
    public List<Vocabulary> searchVocabulary(
            @com.google.adk.tools.Annotations.Schema(name = "terms", description = "List of term names to search for") List<String> terms,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Searching vocabulary for terms: %s (Call ID: %s)",
                terms, toolContext.functionCallId().orElse("N/A")));
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        List<Vocabulary> vocabularies = vocabularyRepository.findByTermIgnoreCaseIn(terms);
        return vocabularies;
    }

    @com.google.adk.tools.Annotations.Schema(name = "getVocabularyByTerm", description = """
        Get a single vocabulary term by its exact term name (case-insensitive).
    """)
    public java.util.Optional<Vocabulary> getVocabularyByTerm(
            @com.google.adk.tools.Annotations.Schema(name = "term", description = "The vocabulary term to search for") String term,
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting vocabulary for term: %s (Call ID: %s)",
                term, toolContext.functionCallId().orElse("N/A")));
        return vocabularyRepository.findByTermIgnoreCase(term);
    }

    @com.google.adk.tools.Annotations.Schema(name = "getExistingRules", description = """
        Get existing rules for reference. Use this to understand the format and structure of rule expressions.
    """)
    public List<Rule> getExistingRules(
            @com.google.adk.tools.Annotations.Schema(name = "toolContext") // Ensures ADK injection
            ToolContext toolContext) {
        logger.log(Level.INFO, String.format("Getting existing rules for reference (Call ID: %s)",
                toolContext.functionCallId().orElse("N/A")));
        return ruleRepository.findAll();
    }

    
}
