package com.example.agent;

import com.example.agent.models.ClassModel;
import com.example.agent.models.EventCapture;
import com.example.agent.models.ClientRegistrationDto;
import com.example.agent.models.TokenRefreshDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for acp-server: registration, token refresh, vocabulary, rules,
 * event captures.
 * Handles auth via Bearer token. Caller is responsible for token refresh on
 * 401.
 */
public class AcpServerClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AcpServerClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "http://localhost:8080" : baseUrl.replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Register client. Response includes token and refreshToken.
     */
    public ClientRegistrationDto.Response register(ClientRegistrationDto.Request req, String bearerToken)
            throws Exception {
        String json = mapper.writeValueAsString(req);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/client/register"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearerToken != null && !bearerToken.isEmpty())
            b.header("Authorization", "Bearer " + bearerToken);

        HttpResponse<String> r = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("Register failed: " + r.statusCode() + " " + r.body());
        return mapper.readValue(r.body(), ClientRegistrationDto.Response.class);
    }

    /**
     * Refresh token. Returns new access token and expiresInMs.
     */
    public TokenRefreshDto.Response refreshToken(TokenRefreshDto.Request req) throws Exception {
        String json = mapper.writeValueAsString(req);
        HttpResponse<String> r = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/auth/token/refresh"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("Token refresh failed: " + r.statusCode() + " " + r.body());
        return mapper.readValue(r.body(), TokenRefreshDto.Response.class);
    }

    /**
     * POST /api/vocabulary with List of ClassModelDto. acp-server expects
     * List&lt;ClassModel&gt; (same JSON shape).
     */
    public int postVocabulary(List<ClassModel> list, String bearerToken) throws Exception {
        return postVocabulary(list, bearerToken, null);
    }

    public int postVocabulary(List<ClassModel> list, String bearerToken, String idempotencyKey) throws Exception {
        if (list == null || list.isEmpty())
            return 0;
        String json = mapper.writeValueAsString(list);
        return post("/api/vocabulary", json, bearerToken, idempotencyKey);
    }

    /**
     * POST /api/vocabulary/rules/process with rule map (eventName, ruleName,
     * ruleDescription, payload).
     */
    public int postRule(Map<String, Object> ruleMap, String bearerToken) throws Exception {
        return postRule(ruleMap, bearerToken, null);
    }

    public int postRule(Map<String, Object> ruleMap, String bearerToken, String idempotencyKey) throws Exception {
        if (ruleMap == null)
            return 0;
        String json = mapper.writeValueAsString(ruleMap);
        return post("/api/vocabulary/rules/process", json, bearerToken, idempotencyKey);
    }

    /**
     * POST /api/event with List of EventCaptureDto. acp-server expects
     * List&lt;EventCapture&gt; (same JSON shape).
     */
    public int postEventCaptures(List<EventCapture> list, String bearerToken) throws Exception {
        return postEventCaptures(list, bearerToken, null);
    }

    public int postEventCaptures(List<EventCapture> list, String bearerToken, String idempotencyKey) throws Exception {
        if (list == null || list.isEmpty())
            return 0;
        String json = mapper.writeValueAsString(list);
        return post("/api/event", json, bearerToken, idempotencyKey);
    }

    private int post(String path, String json, String bearerToken, String idempotencyKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearerToken != null && !bearerToken.isEmpty())
            b.header("Authorization", "Bearer " + bearerToken);
        if (idempotencyKey != null && !idempotencyKey.isBlank())
            b.header("X-Idempotency-Key", idempotencyKey);

        HttpResponse<String> r = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        // 409 Conflict is safely returned if the idiosyncrasy filter blocks as
        // duplicate
        if (r.statusCode() >= 400 && r.statusCode() != 409) {
            throw new RuntimeException("POST " + path + " failed: " + r.statusCode() + " " + r.body());
        }
        return r.statusCode();
    }
}
