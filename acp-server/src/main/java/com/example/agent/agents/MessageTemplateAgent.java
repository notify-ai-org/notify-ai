package com.example.agent.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.examples.Example;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Create a prompt for agent which produces channel-specific message body
 * templates for an event.
 * Channels: Email, SMS, Push, In-App, Web Push, Webhook, Chat/IM, Voice (TTS)
 * Input: { events: [...], payloads: [...] }
 * Output: { templates: [{ eventName, channel, maxLength, typicalFormat,
 * payloadExample, characteristics }] }
 * 
 */
public class MessageTemplateAgent {
  private static final Logger logger = Logger.getLogger(MessageTemplateAgent.class.getName());
  LlmAgent vocabularyGenerator = createMessageTemplateGenerator();

  public LlmAgent createMessageTemplateGenerator() {
    // --- Input Schema (ClassModel) ---
    Schema inputSchema = Schema.builder()
        .title("EventModel")
        .type(Type.Known.OBJECT)
        .description("Represents an Event and its payload schema values and description")
        .properties(Map.of(
            "event", Schema.builder().type(Type.Known.STRING)
                .description("Package name").build(),
            "description", Schema.builder().type(Type.Known.STRING)
                .description("Description of the event occured").build(),
            "payload",Schema.builder().type(Type.Known.OBJECT).description("The payload of the event").build(),
            "occuredAt", Schema.builder().type(Type.Known.STRING)
                .description("The date and time the event occurred").items(
                    Schema.builder().type(Type.Known.STRING).build())
                .build()))
        .build();

    // --- Output Schema (Vocabulary) ---
    Schema outputSchema = Schema.builder()
        .title("Templates")
        .type(Type.Known.ARRAY)
        .description("A list of message templates of a specified channels related to the event occured")
        .items(
            Schema.builder().title("").type(Type.Known.OBJECT).properties(
                Map.of(
                    "channel", Schema.builder().type(Type.Known.STRING)
                        .description("The channel name to send this message through").build(),
                    "subject", Schema.builder().type(Type.Known.STRING)
                        .description("The role of the user to send the message to").build(),
                    "template", Schema.builder().type(Type.Known.STRING)
                        .description("The payload vocabulary embedded message template of the message").build()))
                .build())
        .build();

    // --- Example input/output using proper Content + Part builders ---
    // Example input matching the inputSchema
    Content exampleInput = Content.fromParts(
        Part.fromText("""
            {
              "eventType": "com.example.order.created",
              "description": "An event representing the creation of a customer order.",
              "payload": {
                "orderId": "1234567890",
                "orderAmount": 100,
                "orderStatus": "PLACED"
              },
              "occuredAt": "2024-05-10T09:15:00Z"
            }
            """));

    // Example output matching the outputSchema (List of templates)
    Content exampleOutput = Content.fromParts(
        Part.fromText("""
            [
              {
                "channel": "EMAIL",
                "subject": "customer",
                "template": "Dear Customer, your order (Order ID: ORD-10001) placed on 2024-05-10 has been received.
                 The order amount is $299.99."
              },
              {
                "channel": "SMS",
                "subject": "customer",
                "template": "Order ORD-10001 has been created on 2024-05-10. Total: $299.99."
              }
            ]
            """));

    Example example = Example.builder()
        .input(exampleInput)
        .output(List.of(exampleOutput))
        .build();

    // --- Build the LLM Agent for Message Template Generation ---
    return LlmAgent.builder()
        .name("MessageTemplateGenerator")
        .description("""
                Generates message templates for different communication channels
                (e.g., EMAIL, SMS) based on order or business event data.
            """).instruction("""
                You are a message template generation assistant.
                Given a business event (for example, an order creation or policy update),
                compose business-friendly message templates for customer-facing channels.

                1. For each requested channel (such as EMAIL, SMS), create a template object with fields:
                   - channel: the communication channel name (e.g., EMAIL, SMS)
                   - subject: short subject or title (if applicable for channel)
                   - template: a concise, human-readable message with placeholders or values substituted,
                               summarizing the key details (such as order ID, date, or amount).
                2. Write templates clearly and concisely, using an appropriate style for the target channel.
                3. Tailor the structure and tone for each channel. For SMS, keep messages brief.
                   For EMAIL, you may include slightly more detail and a greeting.
            """)
        .inputSchema(inputSchema)
        .outputSchema(outputSchema)
        .exampleProvider(example)
        .outputKey("templates")
        .build();
  }

  /**
   * @return the vocabularyGenerator
   */
  public LlmAgent getVocabularyGenerator() {
    return vocabularyGenerator;
  }

}
