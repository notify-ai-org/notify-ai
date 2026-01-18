package com.example.sdk.event;

import java.lang.reflect.*;
import java.net.http.*;
import java.net.URI;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wraps annotated business logic methods to capture and stream
 * event payloads dynamically to the Agent Server.
 */
public class EventWrapper {

    private final String agentServerUrl;

    public EventWrapper(String agentServerUrl) {
        this.agentServerUrl = agentServerUrl;
    }

    public Object invokeWithEventCapture(Object target, Method method, Object... args) throws Exception {
        long start = System.currentTimeMillis();

        Object result = method.invoke(target, args);

        long duration = System.currentTimeMillis() - start;
        EventCapturePayload payload = new EventCapturePayload(
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                args,
                result,
                duration,
                Instant.now()
        );

        streamEvent(payload);
        return result;
    }

    private void streamEvent(EventCapturePayload payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(agentServerUrl + "/event-capture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());

            System.out.println("📡 Event captured and sent: " + payload.eventName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
