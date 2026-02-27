package com.example.agent.config;

import com.example.agent.AgentOrchestrator;
import com.example.agent.util.SchemaUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.examples.Example;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Type;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AgentRegistry {

    private final Map<String, String> registry = new ConcurrentHashMap<>();
    private final ResourceLoader resourceLoader;

    // Constants for Agent IDs
    public static final String MESSAGE_TEMPLATE_AGENT_ID = "MessageTemplate";
    public static final String EVENT_SCHEDULER_AGENT_ID = "EventScheduler";
    public static final String RULE_PROCESSOR_AGENT_ID = "RuleProcessor";
    public static final String EVENT_PROCESSOR_AGENT_ID = "EventProcessor";
    public static final String LOG_TO_FACTS_AGENT_ID = "LogToFacts";
    public static final String MEMORY_SUMMARIZER_AGENT_ID = "MemorySummarizer";

    public AgentRegistry(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void put(String name, String id) {
        registry.put(name, id);
    }

    public String get(String name) {
        return registry.get(name);
    }

    @Bean
    public ApplicationRunner agentLoader(AgentOrchestrator agentOrchestrator) {
        return args -> {
            loadAgents(agentOrchestrator);
        };
    }

    private void loadAgents(AgentOrchestrator agentOrchestrator) {
        try {
            ObjectMapper mapper = ObjectMapperFactory.create();
            Resource resource = resourceLoader.getResource("classpath:agents/agents.json");
            List<AgentConfig> configs;
            try (InputStream is = resource.getInputStream()) {
                configs = mapper.readValue(is, new TypeReference<List<AgentConfig>>() {
                });
            }

            for (AgentConfig config : configs) {
                try {
                    Class<?> inputClass = Class.forName(config.getInputClass());
                    Class<?> outputClass = Class.forName(config.getOutputClass());

                    Schema inputSchema = SchemaUtil.schemaForClass(inputClass, config.getInputSchemaTitle(),
                            config.getInputSchemaDescription());

                    Schema outputSchema;
                    if ("ARRAY".equalsIgnoreCase(config.getOutputType())) {
                        outputSchema = Schema.builder()
                                .title(config.getOutputSchemaTitle())
                                .type(Type.Known.ARRAY)
                                .description(config.getOutputSchemaDescription())
                                .items(SchemaUtil.schemaForClass(outputClass, config.getOutputSchemaTitle() + "Item",
                                        "Item for " + config.getOutputSchemaTitle()))
                                .build();
                    } else {
                        outputSchema = SchemaUtil.schemaForClass(outputClass, config.getOutputSchemaTitle(),
                                config.getOutputSchemaDescription());
                    }

                    String prompt = loadPrompt(config.getResourcePath() + "/prompt.md");
                    Example example = loadSingleExample(config.getResourcePath() + "/example.json");

                    String model = config.getModel() != null && !config.getModel().isBlank()
                            ? config.getModel()
                            : "gemini-2.0-flash";

                    GenerateContentConfig generateContentConfig = null;
                    // Enable thought process specifically for thinking-capable models
                    if (model.contains("-thinking-") || model.contains("-2.5-pro") || model.contains("-3.0-")) {
                        generateContentConfig = GenerateContentConfig.builder()
                                .thinkingConfig(ThinkingConfig.builder().includeThoughts(true).build())
                                .build();
                    }

                    LlmAgent agent = LlmAgent.builder()
                            .name(config.getName())
                            .description(config.getDescription())
                            .model(model)
                            .inputSchema(inputSchema)
                            .outputSchema(outputSchema)
                            .instruction(prompt)
                            .exampleProvider(example)
                            .tools(Collections.emptyList())
                            .outputKey(config.getOutputKey())
                            .generateContentConfig(generateContentConfig)
                            .build();

                    String id = agentOrchestrator.registerAgent(agent);
                    registry.put(config.getId(), id);
                    System.out.println("Registered agent: " + config.getId() + " (" + config.getName()
                            + ") with System ID: " + id);

                } catch (Exception e) {
                    System.err.println("Failed to load agent: " + config.getName() + " Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to load agents from agents.json");
            e.printStackTrace();
        }
    }

    private static final ObjectMapper MAPPER = ObjectMapperFactory.create();

    public static String loadPrompt(String resourcePath) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Prompt resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load prompt: " + resourcePath, e);
        }
    }

    /**
     * Load a single Example from a JSON file with shape:
     * { "input": {...}, "output": {... or [...] } }
     */
    public static Example loadSingleExample(String resourcePath) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Example resource not found: " + resourcePath);
            }
            Map<String, Object> root = MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {
            });

            String inputJson = MAPPER.writeValueAsString(root.get("input"));
            String outputJson = MAPPER.writeValueAsString(root.get("output"));

            Content input = Content.fromParts(Part.fromText(inputJson));
            Content output = Content.fromParts(Part.fromText(outputJson));

            return Example.builder()
                    .input(input)
                    .output(List.of(output))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load example: " + resourcePath, e);
        }
    }

    private static InputStream getResourceAsStream(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = AgentRegistry.class.getClassLoader();
        return cl.getResourceAsStream(path);
    }
}
