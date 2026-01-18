package com.example.agent.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.examples.Example;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.Map;

public class EventSchedulerAgent {
        private final LlmAgent eventSchedulerAgent = createEventSchedulerAgent();
       
        // Create on init on pause, on resume callbacks
        private LlmAgent createEventSchedulerAgent() {
                // Create and return the LlmAgent
                // Define input schema: expects an event with metadata and scheduling intentions
                Schema inputSchema = Schema.builder()
                    .title("EventScheduleRequest")
                    .type(Type.Known.OBJECT)
                    .description("Request for generating a Unified Job Schedule (UJS) expression for a notification event.")
                    .properties(Map.of(
                        "eventName", Schema.builder().type(Type.Known.STRING)
                                        .description("Name of the event to trigger the notification, e.g., 'OrderPlaced'")
                                        .build(),
                        "eventDescription", Schema.builder().type(Type.Known.STRING)
                                        .description("Description of the event.").build(),
                        "triggerType", Schema.builder().type(Type.Known.STRING)
                                        .description("Trigger type: event, time, system")
                                        .build(),
                        "scheduleIntent", Schema.builder().type(Type.Known.STRING)
                                        .description(
            "Natural language description of when and how notifications should be sent. E.g., 'Send notification immediately after event and repeat daily for 3 days.'")
                                        .build(),
                        "preferredTimeWindow", Schema.builder().type(Type.Known.STRING)
                                        .description("Preferred window for notifications, e.g., '09:00-18:00', optional.")
                                        .build()))
                    .build();

                // Define output schema: a plain string of the UJS expression
                Schema outputSchema = Schema.builder()
                                .title("EventSchedule")
                                .type(Type.Known.STRING)
                                .description("A list of triggers for the event")
                                .build();

                // Example input and output
                com.google.genai.types.Content exampleInput = com.google.genai.types.Content.fromParts(
                                com.google.genai.types.Part.fromText(
                                                """
                      {
                        "eventName": "OrderPlaced",
                        "eventDescription": "A new order is placed by the user",
                        "triggerType": "event",
                        "scheduleIntent": "Send notification immediately and again after 24 hours, only during business hours",
                        "preferredTimeWindow": "09:00-18:00",
                      }
                  """));

                com.google.genai.types.Content exampleOutput = com.google.genai.types.Content.fromParts(
                                com.google.genai.types.Part.fromText("""
                      [
                          {
                              "triggerType": "event",
                              "triggerValue": "immediate"
                          },
                          {
                              "triggerType": "cron",
                              "triggerValue": "0 0 9-18/1 ? * *"
                          }
                      ]
                  """));

                Example example = Example.builder()
                                .input(exampleInput)
                                .output(java.util.List.of(exampleOutput))
                                .build();

                return LlmAgent.builder()
                                .name("Event Notification Scheduler Agent")
                                .description("Generates notification schedules for given events")
                                .inputSchema(inputSchema)
                                .outputSchema(outputSchema)
                                .instruction( """
            You are an expert event-scheduling AI trained to interpret natural-language scheduling rules and convert them into Quartz Scheduler–compatible triggers.
            Your task is to analyze a given event definition and produce a list of notification job triggers, where each trigger has:

            1. triggerType
            One of:
            "cron" (CronTrigger)
            "simple" (SimpleTrigger)
            "calendar" (CalendarIntervalTrigger)
            "event" (Immediate or event-driven trigger — no schedule required)

            2. triggerValue
            Depending on triggerType:
                cron → Quartz cron expression (e.g., "0 0 9 * * ?")
                simple → comma-separated values representing "startDelay, repeatInterval, repeatCount" example: "0s,24h,1"
                calendar → calendar interval definition
                example: "every 24 hours"
                event → "immediate"

            ❗ Rules you must follow:
            A. Interpret the natural-language schedule intent
            Understand references like “immediately”, “every 24 hours”, “during business hours”, “only weekdays”, “until 2 notifications”, etc.
            Resolve relative constraints (time windows, end conditions, limits on repeat count).

            B. Respect additional metadata fields
            preferredTimeWindow ⇒ restrict trigger firing to that window
            endCondition ⇒ set repeat count or termination logic
            triggerType may override scheduling logic (“event”, “cron”, “simple”, “calendar”)

            C. Output Format
            Respond ONLY with a JSON array of trigger objects:
            [
              {
                "triggerType": "cron | simple | calendar | event",
                "triggerValue": "string"
              }
            ]
            No explanation, no prose — just valid JSON.

            ❗ Behavior Guide
            1. If the schedule contains “immediately” or other references to immediate execution:
            Create an event trigger:
            { "triggerType": "event", "triggerValue": "immediate" }
            Example: "Send notification immediately after event"

            2. If schedule mentions fixed intervals ("after 24 hours", “every 2 days”):
            Create a simple or calendar trigger depending on scale:
            Hours/minutes → simple
            Days/weeks/months → calendar

            3. If schedule includes strict times (“between 09:00-18:00”):
            Use:
            Cron trigger restricted to that range
            Or generate multiple cron triggers if needed
            Ensure time falls within the window

            4. End Conditions
            Examples:
            "after 2 notifications" → repeatCount = 2
            "until date" → stop by endAt date

            5. Always produce Quartz-compatible values
            Examples:
            Cron format: "0 0 10 * * ?"
            Simple: "0s,24h,1"
            Calendar: "every 24 hours"

            (You may adjust the specific cron rule depending on how you interpret the 24-hour delay + time window alignment.)
          """)
          .exampleProvider(example)
          .tools()
          .outputKey("scheduleTrigger")
          .build();
        }

        /**
         * @return the summarizerAgent
         */
        public LlmAgent getSummarizerAgent() {
                return eventSchedulerAgent;
        }
}
