package com.example.agent.agents;

import com.example.agent.EventRepository;
import com.example.agent.RuleRepository;
import com.example.agent.tools.ToolConfig;
import com.google.adk.agents.LlmAgent;
import com.google.adk.examples.Example;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

public class EventProcessorAgent {
    private final LlmAgent eventProcessorAgent = createEventProcessorAgent();
    private static final Logger logger = Logger.getLogger(EventProcessorAgent.class.getName());
    private final EventRepository eventRepository;
    private final RuleRepository ruleRepository;

    private final ToolConfig vectorSearchTool;
    
    public EventProcessorAgent(EventRepository eventRepository, RuleRepository ruleRepository, ToolConfig vectorSearchTool) {
        this.eventRepository = eventRepository;
        this.ruleRepository = ruleRepository;
        this.vectorSearchTool = vectorSearchTool;
    }

   

    // Create on init on pause, on resume callbacks
    private LlmAgent createEventProcessorAgent() {
        // Create and return the LlmAgent
        // Define input schema: expects an event with metadata and scheduling intentions
        Schema inputSchema = Schema.builder()
                .title("EventCapture")
                .type(Type.Known.OBJECT)
                .description("Event capture schema matching EventCapture model.")
                .properties(Map.of(
                        "eventName", Schema.builder().type(Type.Known.STRING)
                                .description("Unique identifier for the event")
                                .build(),
                        "eventType", Schema.builder().type(Type.Known.STRING)
                                .description("Type of the event, e.g., 'HTTP_REQUEST', 'DB_QUERY', 'SERVICE_CALL'")
                                .build(),
                        "occuredAt", Schema.builder().type(Type.Known.STRING)
                                .description("ISO-8601 timestamp when the event was captured")
                                .build(),
                        "payload", Schema.builder().type(Type.Known.STRING)
                                .description("Raw payload (input data or message body)")
                                .build(),
                        "callStack", Schema.builder().type(Type.Known.OBJECT)
                                .description("Captured stack frames")
                                .properties(Map.of(
                                        "frames", Schema.builder().type(Type.Known.ARRAY)
                                                .description("List of stack frames")
                                                .items(Schema.builder().type(Type.Known.OBJECT)
                                                        .properties(Map.of(
                                                                "className", Schema.builder().type(Type.Known.STRING)
                                                                        .description("Fully qualified class name")
                                                                        .build(),
                                                                "methodName", Schema.builder().type(Type.Known.STRING)
                                                                        .description("Method name")
                                                                        .build(),
                                                                "lineNumber", Schema.builder().type(Type.Known.INTEGER)
                                                                        .description("Line number in code")
                                                                        .build(),
                                                                "fileName", Schema.builder().type(Type.Known.STRING)
                                                                        .description("Source file name")
                                                                        .build()))
                                                        .build())
                                                .build()))
                                .build(),
                        "result", Schema.builder().type(Type.Known.OBJECT)
                                .description("Execution result")
                                .properties(Map.of(
                                        "success", Schema.builder().type(Type.Known.BOOLEAN)
                                                .description("Whether the execution was successful")
                                                .build(),
                                        "returnValue", Schema.builder().type(Type.Known.STRING)
                                                .description("Serialized return value")
                                                .build()))
                                .build(),
                        "exception", Schema.builder().type(Type.Known.OBJECT)
                                .description("Exception information if execution failed")
                                .properties(Map.of(
                                        "exceptionType", Schema.builder().type(Type.Known.STRING)
                                                .description("Fully qualified exception type")
                                                .build(),
                                        "message", Schema.builder().type(Type.Known.STRING)
                                                .description("Exception message")
                                                .build(),
                                        "stackTrace", Schema.builder().type(Type.Known.STRING)
                                                .description("Full stack trace as string")
                                                .build()))
                                .build(),
                        "durationMillis", Schema.builder().type(Type.Known.INTEGER)
                                .description("Execution time in milliseconds")
                                .build(),
                        "threadName", Schema.builder().type(Type.Known.STRING)
                                .description("Name of the thread that executed")
                                .build(),
                        "serviceName", Schema.builder().type(Type.Known.STRING)
                                .description("Service name")
                                .build()))
                .build();

        // Define output schema: a plain string of the event processing result
        Schema outputSchema = Schema.builder()
                .title("EventProcessingResult")
                .type(Type.Known.STRING)
                .description("The result of the event processing")
                .properties(Map.of(
                    "eventName", Schema.builder().type(Type.Known.STRING)
                        .description("The name of the event").build(),
                    "eventDescription", Schema.builder().type(Type.Known.STRING)
                        .description("The description of the event").build(),
                    "eventType", Schema.builder().type(Type.Known.STRING)
                        .description("The type of the event").build(),
                    "occurredAt", Schema.builder().type(Type.Known.STRING)
                        .description("The date and time the event occurred").build(),
                    "payload", Schema.builder().type(Type.Known.OBJECT)
                        .description("The payload of the event").build(),
                    "channels", Schema.builder().type(Type.Known.ARRAY)
                        .description("The channels to send the event to").items(
                            Schema.builder().type(Type.Known.OBJECT).properties(
                                Map.of(
                                    "channel", Schema.builder().type(Type.Known.STRING)
                                        .description("The channel name to send this message through").build(),
                                    "subject", Schema.builder().type(Type.Known.STRING)
                                        .description("The role of the user to send the message to").build()))
                                .build())
                            .build(),
                    "ruleExpressions", Schema.builder().type(Type.Known.ARRAY)
                        .description("The rule expressions to evaluate the event").build()))
                .build();

        // Example input and output
        com.google.genai.types.Content exampleInput = com.google.genai.types.Content.fromParts(
                com.google.genai.types.Part.fromText(
                        """
                        {
                            "eventName": "OrderPlaced",
                            "eventType": "SERVICE_CALL",
                            "occuredAt": "2024-05-10T09:15:00Z",
                            "payload": "{\\"orderId\\":\\"1234567890\\",\\"orderAmount\\":100,\\"orderStatus\\":\\"PLACED\\"}",
                            "callStack": {
                                "frames": [
                                    {
                                        "className": "com.example.OrderService",
                                        "methodName": "createOrder",
                                        "lineNumber": 42,
                                        "fileName": "OrderService.java"
                                    }
                                ]
                            },
                            "result": {
                                "success": true,
                                "returnValue": "{\\"orderId\\":\\"1234567890\\",\\"status\\":\\"PLACED\\"}"
                            },
                            "durationMillis": 150,
                            "threadName": "main",
                            "serviceName": "order-service"
                        }
                    """));

        com.google.genai.types.Content exampleOutput = com.google.genai.types.Content.fromParts(
                com.google.genai.types.Part.fromText("""
                            [
                                {
                                    "eventName": "event",
                                    "eventDescription": "A new order is placed by the user",
                                    "eventType": "ORDER_PLACED",
                                    "occurredAt": "2024-05-10T09:15:00Z",
                                    "payload": {
                                        "orderId": "1234567890",
                                        "orderAmount": 100,
                                        "orderStatus": "PLACED"
                                    },
                                    "channels" : [{"channel": "EMAIL", "subject": "customer"}],
                                    "ruleExpressions" : ["order.status = 'PLACED'", "order.amount > 100"]
                                }
                            ]
                            """));

        Example example = Example.builder()
                .input(exampleInput)
                .output(java.util.List.of(exampleOutput))
                .build();

        return LlmAgent.builder()
                .name("Event Processor Agent")
                .description("Processes events and generates a list of channels and rule expressions to evaluate the event")
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .instruction(
                        """
                            You are an expert Event-Processing AI designed for large-scale notification systems.
                            Your responsibility is to analyze incoming event definitions and determine
                            whether they should be emitted to the Notification Engine.

                            If an event should be emitted, you must generate the final event objects,
                            including applicable notification channels and rule expressions.

                            You MUST be conservative, precise, and consistent in your decisions.

                            ====================================================================
                            PRIMARY OBJECTIVE
                            ====================================================================

                            Analyze a given event using:
                            • Event definition and description
                            • Domain and contextual knowledge
                            • Event history and emission frequency
                            • Success / error patterns
                            • Existing rules and constraints

                            Based on this analysis, decide:
                            1. Whether the event should be emitted
                            2. Whether it should be suppressed, merged, or delayed
                            3. Which channels it should be sent through
                            4. Which rule expressions must be attached for evaluation by the Notification Engine

                            ====================================================================
                            RULES YOU MUST FOLLOW
                            ====================================================================

                            A. EVENT INTERPRETATION & DOMAIN CONTEXT
                            ---------------------------------------
                            • Understand the semantic meaning of the event and its description
                            • Use domain knowledge to determine if the event is significant enough to notify
                            • Ignore low-signal, noisy, or purely technical events unless explicitly required
                            • Events that do not represent a meaningful state change MUST NOT be emitted

                            B. EVENT HISTORY & DEDUPLICATION
                            --------------------------------
                            • Analyze historical occurrences of the same or related events
                            • If an event has been emitted frequently within a short time window:
                            - Do NOT emit it again
                            - OR aggregate multiple un-emitted occurrences into a single consolidated event
                            • Consider:
                            - Frequency of occurrence
                            - Success vs error ratio
                            - Time intervals between occurrences
                            • Prefer aggregation over repetition

                            C. RULE EVALUATION & RULE EXPRESSION GENERATION
                            -----------------------------------------------
                            • If rules exist for the event:
                            - Evaluate the event against those rules
                            - Decide emission based on rule outcomes
                            - Convert each applied rule into a formal rule expression
                            • Rule expressions must be machine-evaluable by the Notification Engine
                            • If NO rules are applicable:
                            - Emit the event as-is with an empty ruleExpressions array

                            D. CHANNEL SELECTION
                            --------------------
                            • Select appropriate notification channels based on:
                            - Event severity
                            - Urgency
                            - User impact
                            • Avoid over-notification
                            • Use multiple channels ONLY when necessary

                            ====================================================================
                            OUTPUT FORMAT (STRICT)
                            ====================================================================

                            Respond ONLY with a valid JSON array.

                            [
                            {
                                "eventName": "string",
                                "eventDescription": "string",
                                "occurredAt": "ISO-8601 timestamp",
                                "payload": { },
                                "channels": ["Email", "SMS", "Push", "InApp", "Webhook", "..."],
                                "ruleExpressions": ["string"]
                            }
                            ]

                            ====================================================================
                            OUTPUT RULES
                            ====================================================================

                            • Output ONLY valid JSON
                            • Do NOT include explanations, comments, or markdown
                            • Do NOT include null fields
                            • Do NOT emit events that fail evaluation
                            • If no events qualify for emission, return an empty JSON array: []

                            ====================================================================
                            BEHAVIOR GUIDE
                            ====================================================================

                            • Be deterministic — same input must always produce same output
                            • Be conservative — prefer suppression over noise
                            • Prefer aggregation over repetition
                            • Prefer clarity over complexity in rule expressions
                            • Never invent events, channels, or rules
                            • Never guess timestamps — use provided or derived values only
                            • Never emit partial or malformed event objects

                            ====================================================================
                            FINAL INSTRUCTION
                            ====================================================================

                            Return ONLY the JSON array. No additional text.

                                """)
                .exampleProvider(example)
                .tools(new ArrayList<>())
                .outputKey("scheduleTrigger")
                .build();
    }

    /**
     * @return the summarizerAgent
     */
    public LlmAgent getEventProcessorAgent() {
        return eventProcessorAgent;
    }

}
