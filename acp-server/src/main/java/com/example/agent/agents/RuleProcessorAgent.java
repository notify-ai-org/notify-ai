package com.example.agent.agents;

import com.example.agent.RuleRepository;
import com.example.agent.VocabularyRepository;
import com.google.adk.agents.LlmAgent;
import com.google.adk.examples.Example;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.Map;
import java.util.logging.Logger;

public class RuleProcessorAgent {
    private final LlmAgent ruleProcessorAgent = createRuleProcessorAgent();
    private static final Logger logger = Logger.getLogger(RuleProcessorAgent.class.getName());
    private final RuleRepository ruleRepository;
    private final VocabularyRepository vocabularyRepository;

    public RuleProcessorAgent(RuleRepository ruleRepository, VocabularyRepository vocabularyRepository) {
        this.ruleRepository = ruleRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    private LlmAgent createRuleProcessorAgent() {
        // Input schema: natural language rule definition
        Schema inputSchema = Schema.builder()
                .title("RuleDefinitionRequest")
                .type(Type.Known.OBJECT)
                .description("Request for converting a natural language rule definition to an executable expression.")
                .properties(Map.of(
                        "eventName", Schema.builder().type(Type.Known.STRING)
                                .description("Name of the event this rule applies to, e.g., 'OrderPlaced'")
                                .build(),
                        "ruleDescription", Schema.builder().type(Type.Known.STRING)
                                .description("Natural language description of the rule, e.g., 'Send notification when order amount is greater than 100 and order status is PLACED'")
                                .build(),
                        "ruleName", Schema.builder().type(Type.Known.STRING)
                                .description("Name of the rule, e.g., 'HighValueOrderRule'")
                                .build(),
                        "payload", Schema.builder().type(Type.Known.OBJECT)
                                .description("Example payload structure to help understand available fields")
                                .build()))
                .build();

        // Output schema: executable rule expression
        Schema outputSchema = Schema.builder()
                .title("RuleExpression")
                .type(Type.Known.OBJECT)
                .description("Executable rule expression using vocabulary terms")
                .properties(Map.of(
                        "ruleName", Schema.builder().type(Type.Known.STRING)
                                .description("Name of the rule")
                                .build(),
                        "conditionExpr", Schema.builder().type(Type.Known.STRING)
                                .description("Executable expression using vocabulary terms and operators (e.g., SpEL, MVEL, or SQL-like DSL). Use vocabulary terms from the vocabulary database, not raw field names.")
                                .build(),
                        "vocabularyTerms", Schema.builder().type(Type.Known.ARRAY)
                                .description("List of vocabulary terms used in the expression")
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "explanation", Schema.builder().type(Type.Known.STRING)
                                .description("Brief explanation of how the natural language was converted to the expression")
                                .build()))
                .build();

        // Example input and output
        com.google.genai.types.Content exampleInput = com.google.genai.types.Content.fromParts(
                com.google.genai.types.Part.fromText(
                        """
                        {
                            "eventName": "OrderPlaced",
                            "ruleName": "HighValueOrderRule",
                            "ruleDescription": "Send notification when order amount is greater than 100 and order status is PLACED",
                            "payload": {
                                "orderId": "1234567890",
                                "orderAmount": 150,
                                "orderStatus": "PLACED"
                            }
                        }
                        """));

        com.google.genai.types.Content exampleOutput = com.google.genai.types.Content.fromParts(
                com.google.genai.types.Part.fromText("""
                        {
                            "ruleName": "HighValueOrderRule",
                            "conditionExpr": "orderAmount > 100 && orderStatus == 'PLACED'",
                            "vocabularyTerms": ["orderAmount", "orderStatus"],
                            "explanation": "Converted natural language to expression using vocabulary terms: orderAmount for order amount field and orderStatus for order status field. Used > operator for 'greater than' and && for 'and' condition."
                        }
                        """));

        Example example = Example.builder()
                .input(exampleInput)
                .output(java.util.List.of(exampleOutput))
                .build();

        return LlmAgent.builder()
                .name("Rule Processor Agent")
                .description("Converts natural language rule definitions to executable expressions using stored vocabulary terms")
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .instruction(
                        """
                            You are an expert Rule Processing AI designed to convert natural language rule definitions
                            into executable expressions that can be evaluated by a notification engine.

                            ====================================================================
                            PRIMARY OBJECTIVE
                            ====================================================================

                            Convert natural language rule descriptions into executable expressions that:
                            1. Use vocabulary terms from the stored vocabulary database
                            2. Are machine-evaluable (SpEL, MVEL, SQL-like DSL, or similar)
                            3. Accurately represent the intent of the natural language description

                            ====================================================================
                            RULES YOU MUST FOLLOW
                            ====================================================================

                            A. VOCABULARY TERM USAGE
                            -----------------------
                            • ALWAYS use vocabulary terms from the vocabulary database, NOT raw field names
                            • Before creating an expression, search for vocabulary terms using the searchVocabulary tool
                            • Match natural language terms to vocabulary terms (e.g., "order amount" -> "orderAmount")
                            • If a vocabulary term doesn't exist, use a reasonable camelCase name based on the description
                            • Vocabulary terms should follow the domain model structure

                            B. EXPRESSION SYNTAX
                            -------------------
                            • Use standard comparison operators: ==, !=, >, <, >=, <=
                            • Use logical operators: && (AND), || (OR), ! (NOT)
                            • Use parentheses for grouping: (condition1 && condition2) || condition3
                            • String comparisons should use quotes: field == 'VALUE'
                            • Numeric comparisons: field > 100
                            • Boolean comparisons: field == true

                            C. EXPRESSION STRUCTURE
                            ----------------------
                            • Keep expressions simple and readable
                            • Use vocabulary terms that match the payload structure
                            • Ensure expressions can be evaluated against event payloads
                            • Avoid complex nested logic when possible

                            D. VOCABULARY SEARCH STRATEGY
                            -----------------------------
                            • Extract key terms from the natural language description
                            • Search for vocabulary terms that match these concepts
                            • Use the most appropriate vocabulary term based on description and context
                            • If multiple terms match, prefer the most specific one

                            ====================================================================
                            OUTPUT FORMAT (STRICT)
                            ====================================================================

                            Respond ONLY with a valid JSON object matching the output schema:

                            {
                                "ruleName": "string",
                                "conditionExpr": "executable expression using vocabulary terms",
                                "vocabularyTerms": ["term1", "term2", ...],
                                "explanation": "brief explanation of the conversion"
                            }

                            ====================================================================
                            EXAMPLES
                            ====================================================================

                            Natural Language: "Send notification when order amount is greater than 100"
                            Expression: "orderAmount > 100"
                            Vocabulary Terms: ["orderAmount"]

                            Natural Language: "Notify if order status is PLACED and amount exceeds 50"
                            Expression: "orderStatus == 'PLACED' && orderAmount > 50"
                            Vocabulary Terms: ["orderStatus", "orderAmount"]

                            Natural Language: "Alert when payment failed or order is cancelled"
                            Expression: "paymentStatus == 'FAILED' || orderStatus == 'CANCELLED'"
                            Vocabulary Terms: ["paymentStatus", "orderStatus"]

                            ====================================================================
                            FINAL INSTRUCTION
                            ====================================================================

                            • Always search vocabulary before creating expressions
                            • Use vocabulary terms, not raw field names
                            • Return ONLY valid JSON matching the output schema
                            • Include all vocabulary terms used in the vocabularyTerms array
                            • Provide a clear explanation of the conversion

                            """)
                .exampleProvider(example)
                .outputKey("ruleExpression")
                .build();
    }

    /**
     * @return the ruleProcessorAgent
     */
    public LlmAgent getRuleProcessorAgent() {
        return ruleProcessorAgent;
    }
}

