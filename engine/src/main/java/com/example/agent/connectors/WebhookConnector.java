package com.example.agent.connectors;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.agent.AbstractNotificationConnector;
import com.example.agent.models.NotificationJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebhookConnector extends AbstractNotificationConnector {

    private final RestTemplate webhookRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public String channel() {
        return "webhook";
    }
    
    private boolean enabled = true;
    private int defaultTimeoutMs = 5000;

    @Override
    public void send(NotificationJob job) {

        validate(job);

        Map<String, String> attrs =
                job.getAttributes() == null ? Map.of() : job.getAttributes();

        try {
            String body = buildBody(job);
            HttpMethod method = resolveMethod(attrs);
            org.springframework.http.HttpHeaders headers = buildHeaders(job, body, attrs);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    webhookRestTemplate.exchange(
                            job.getTarget(),
                            method,
                            entity,
                            String.class
                    );

            if (!isSuccess(response.getStatusCode(), attrs)) {
                throw new RuntimeException(
                        "Webhook delivery failed, status=" + response.getStatusCodeValue()
                );
            }

        } catch (RestClientException ex) {
            // Timeouts, IO, DNS, connection refused
            throw new RuntimeException(
                    "Webhook request failed for notificationId=" + job.getId(), ex
            );
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Webhook send failed for notificationId=" + job.getId(), ex
            );
        }
    }

    // ---------------- helpers ----------------

    private void validate(NotificationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is null");
        }
        if (job.getTarget() == null || job.getTarget().isBlank()) {
            throw new IllegalArgumentException("Webhook URL missing");
        }
    }

    private String buildBody(NotificationJob job) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", job.getId());
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("payload", job.getTemplate());

        return objectMapper.writeValueAsString(envelope);
    }

    private HttpMethod resolveMethod(Map<String, String> attrs) {
        String raw = attrs.getOrDefault("method", "POST");
        return HttpMethod.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    private org.springframework.http.HttpHeaders buildHeaders(
            NotificationJob job,
            String body,
            Map<String, String> attrs) {

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // Idempotency / tracing
        headers.set("X-Notification-Id", job.getId());
        headers.add("X-Notification-Channel", "webhook");

        // Optional signing
        String secret = attrs.get("secret");
        if (secret != null && !secret.isBlank()) {
            headers.add("X-Signature", sign(secret, body));
        }

        // Custom headers: headers.X-Foo=bar
        attrs.forEach((k, v) -> {
            if (k.startsWith("headers.")) {
                headers.add(k.substring("headers.".length()), v);
            }
        });

        return headers;
    }

    private boolean isSuccess(HttpStatusCode status, Map<String, String> attrs) {
        String expected = attrs.get("expectedStatus");
        if (expected != null) {
            return Integer.parseInt(expected) == status.value();
        }
        return status.is2xxSuccessful();
    }

    private String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
                )
            );
            byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook signing failed", e);
        }
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

}

