package com.example.agent.config;

import com.example.agent.AgentOrchestrator;
import com.example.agent.util.ObjectMapperFactory;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AgentRegistry {

    private final Map<String, String> registry = new ConcurrentHashMap<>();

    /**
     * Model name → context-window token limit, loaded from {@code agents/models.json}.
     * Used to derive the {@code tokenBudget} for context assembly.
     */
    private final Map<String, Integer> modelTokenLimits = new ConcurrentHashMap<>();

    /**
     * Agent functional ID → resolved token limit (from the model assigned to that agent).
     * Populated during {@link #loadAgents(AgentOrchestrator)}.
     */
    private final Map<String, Integer> agentTokenLimits = new ConcurrentHashMap<>();

    private static final int DEFAULT_TOKEN_LIMIT = 8000;
    private final ResourceLoader resourceLoader;
    private final com.example.agent.tools.ToolConfig toolConfig;

    // Constants for Agent IDs
    public static final String MESSAGE_TEMPLATE_AGENT_ID = "MessageTemplate";
    public static final String EVENT_SCHEDULER_AGENT_ID = "EventScheduler";
    public static final String RULE_PROCESSOR_AGENT_ID = "RuleProcessor";
    public static final String EVENT_PROCESSOR_AGENT_ID = "EventProcessor";
    public static final String LOG_TO_FACTS_AGENT_ID = "LogToFacts";
    public static final String MEMORY_SUMMARIZER_AGENT_ID = "MemorySummarizer";
    public static final String EVENT_SUMMARIZER_AGENT_ID = "EventSummarizer";

    public AgentRegistry(ResourceLoader resourceLoader, com.example.agent.tools.ToolConfig toolConfig) {
        this.resourceLoader = resourceLoader;
        this.toolConfig = toolConfig;
    }

    public void put(String name, String id) {
        registry.put(name, id);
    }

    public String get(String name) {
        return registry.get(name);
    }

    /**
     * Returns the context-window token limit for the model assigned to the given agent.
     * Falls back to {@value #DEFAULT_TOKEN_LIMIT} if the agent or model is unknown.
     */
    public int getTokenLimitForAgent(String agentId) {
        return agentTokenLimits.getOrDefault(agentId, DEFAULT_TOKEN_LIMIT);
    }

    /**
     * Returns the token limit for a model by name.
     * Falls back to {@value #DEFAULT_TOKEN_LIMIT} if unknown.
     */
    public int getTokenLimitForModel(String modelName) {
        return modelTokenLimits.getOrDefault(modelName, DEFAULT_TOKEN_LIMIT);
    }

    @Bean
    public ApplicationRunner agentLoader(AgentOrchestrator agentOrchestrator) {
        return args -> {
            loadModelTokenLimits();
            loadAgents(agentOrchestrator);
        };
    }

    /** Loads {@code agents/models.json} into {@link #modelTokenLimits}. */
    private void loadModelTokenLimits() {
        try {
            ObjectMapper mapper = ObjectMapperFactory.create();
            Resource resource = resourceLoader.getResource("classpath:agents/models.json");
            try (InputStream is = resource.getInputStream()) {
                Map<String, Integer> limits = mapper.readValue(is, new TypeReference<Map<String, Integer>>() {});
                modelTokenLimits.putAll(limits);
                System.out.println("Loaded token limits for " + limits.size() + " models from models.json");
            }
        } catch (Exception e) {
            System.err.println("Could not load models.json — using default token limit of " + DEFAULT_TOKEN_LIMIT);
        }
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
                    Schema inputSchema = SchemaUtil.schemaForClass(inputClass, config.getInputSchemaTitle(),
                            config.getInputSchemaDescription());

                    Schema outputSchema = null;
                    if (config.getOutputClass() != null && !config.getOutputClass().isBlank()) {
                        Class<?> outputClass = Class.forName(config.getOutputClass());
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

                    java.util.List<com.google.adk.tools.BaseTool> agentTools = new java.util.ArrayList<>();
                    if (config.getTools() != null && !config.getTools().isEmpty()) {
                        for (String toolName : config.getTools()) {
                            agentTools.add(com.google.adk.tools.FunctionTool.create(toolConfig, toolName));
                        }
                    }

                    LlmAgent.Builder agentBuilder = LlmAgent.builder()
                            .name(config.getName())
                            .description(config.getDescription())
                            .model(model)
                            .inputSchema(inputSchema)
                            .instruction(prompt)
                            .exampleProvider(example)
                            .tools(agentTools)
                            .generateContentConfig(generateContentConfig);

                    // ADK 0.2.0: outputSchema cannot co-exist with tools or transfers
                    if (agentTools.isEmpty() && outputSchema != null) {
                        agentBuilder.outputSchema(outputSchema)
                                .outputKey(config.getOutputKey())
                                .disallowTransferToParent(true)
                                .disallowTransferToPeers(true);
                    }

                    LlmAgent agent = agentBuilder.build();

                    int instanceCount = config.getInstances() > 0 ? config.getInstances() : 1;

                    for (int i = 0; i < instanceCount; i++) {
                        String id = agentOrchestrator.registerAgent(config.getId(), agent);
                        System.out.println("Registered instance " + (i + 1) + "/" + instanceCount + " for agent: "
                                + config.getId() + " (" + config.getName() + ") with System ID: " + id);
                    }

                    registry.put(config.getId(), config.getId()); // Map functional ID to functional ID (type)

                    // Track per-agent token limit using the agent's model
                    int tokenLimit = modelTokenLimits.getOrDefault(model, DEFAULT_TOKEN_LIMIT);
                    agentTokenLimits.put(config.getId(), tokenLimit);

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
