package com.example.agent.config;

import com.example.agent.AgentLogRepository;
import com.example.agent.AgentOrchestrator;
import com.example.agent.AgentSnapshotRepository;
import com.example.agent.EventRepository;
import com.example.agent.RuleRepository;
import com.example.agent.VocabularyRepository;
import com.example.agent.agents.MessageTemplateAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AgentRegistry {

    private final Map<String, String> registry = new ConcurrentHashMap<>();

    public void put(String name, String id) {
        registry.put(name, id);
    }

    public String get(String name) {
        return registry.get(name);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(AgentSnapshotRepository snapshotRepo, AgentLogRepository logRepo) {
        return new AgentOrchestrator(snapshotRepo, logRepo);
    }

    // ==== Agent Name Constants ====
    public static final String MESSAGE_TEMPLATE_AGENT_ID = "MessageTemplate";
    public static final String EVENT_SCHEDULER_AGENT_ID = "EventScheduler";
    public static final String RULE_PROCESSOR_AGENT_ID = "RuleProcessor";
    public static final String EVENT_PROCESSOR_AGENT_ID = "EventProcessor";

    // ==== EventProcessorAgent Bean ====
    @Bean
    public com.example.agent.agents.EventProcessorAgent eventProcessorAgent(
            AgentOrchestrator orchestrator,
            EventRepository eventRepository,
            VocabularyRepository vocabularyRepository) {
        com.example.agent.agents.EventProcessorAgent agent =
            new com.example.agent.agents.EventProcessorAgent(eventRepository, vocabularyRepository);
        String id = orchestrator.registerAgent(agent.getEventProcessorAgent());
        registry.put(EVENT_PROCESSOR_AGENT_ID, id);
        return agent;
    }

    @Bean
    public MessageTemplateAgent messageTemplateAgent(AgentOrchestrator orchestrator) {
        MessageTemplateAgent agent = new MessageTemplateAgent();
        String id = orchestrator.registerAgent(agent.createMessageTemplateGenerator());
        registry.put("MessageTemplate", id);
        return agent;
    }

    @Bean
    public com.example.agent.agents.EventSchedulerAgent eventSchedulerAgent(
            AgentOrchestrator orchestrator,
            EventRepository eventRepository) {
        com.example.agent.agents.EventSchedulerAgent agent = new com.example.agent.agents.EventSchedulerAgent();
        String id = orchestrator.registerAgent(agent.getSummarizerAgent());
        registry.put("EventScheduler", id);
        return agent;
    }

    @Bean
    public com.example.agent.agents.RuleProcessorAgent ruleProcessorAgent(
            AgentOrchestrator orchestrator,
            RuleRepository ruleRepository,
            VocabularyRepository vocabularyRepository) {
        com.example.agent.agents.RuleProcessorAgent agent = new com.example.agent.agents.RuleProcessorAgent(
                ruleRepository, vocabularyRepository);
        String id = orchestrator.registerAgent(agent.getRuleProcessorAgent());
        registry.put("RuleProcessor", id);
        return agent;
    }

}
