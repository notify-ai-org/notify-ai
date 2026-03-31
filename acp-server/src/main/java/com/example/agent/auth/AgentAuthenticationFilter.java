package com.example.agent.auth;

import com.example.agent.AgentContextHolder;
import com.example.agent.models.AgentContext;
import com.example.agent.models.AgentSessionEntity;
import com.example.agent.service.DomainContentService;
import com.example.agent.service.SessionService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.agent.annotations.ManagedConfiguration;
import com.example.agent.annotations.ManagedConfiguration.ConfigSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * Authentication filter that: validates JWT, verifies client id and scope,
 * creates AgentContext, loads domain content and session history from the
 * database,
 * and sets AgentContextHolder. On failure returns 401.
 */
@Component
@Order(1)
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final DomainContentService domainContentService;
    private final StringRedisTemplate redisTemplate;
    private final String[] skipPaths;

    @ManagedConfiguration(key = "acp.auth.jwt.secret", source = ConfigSource.CONFIG_MAP)
    String secret;
    @ManagedConfiguration(key = "acp.auth.jwt.required-scope", source = ConfigSource.CONFIG_MAP)
    String requiredScope;
    
    @Value("${acp.idempotency.retry-count:5}")
    @ManagedConfiguration(key = "acp.idempotency.retry-count", source = ConfigSource.CONFIG_MAP)
    private int idempotencyRetryCount;

    @Value("${acp.idempotency.retry-interval-ms:500}")
    @ManagedConfiguration(key = "acp.idempotency.retry-interval-ms", source = ConfigSource.CONFIG_MAP)
    private long idempotencyRetryIntervalMs;

    @Value("${acp.idempotency.expiry-seconds:86400}")
    @ManagedConfiguration(key = "acp.idempotency.expiry-seconds", source = ConfigSource.CONFIG_MAP)
    private long idempotencyExpirySeconds;

    private final SecretKey key;

    public AgentAuthenticationFilter(
            SessionService sessionService,
            DomainContentService domainContentService,
            StringRedisTemplate redisTemplate,
            @Value("${acp.auth.skip-paths:/api/client/register,/api/auth/token/refresh,/actuator/health,/actuator/info}") String skipPathsCsv,
            @Value("${acp.auth.jwt.secret:wsws}") String secret,
            @Value("${acp.auth.jwt.required-scope:agent:invoke}") String requiredScope) {
        this.sessionService = sessionService;
        this.domainContentService = domainContentService;
        this.redisTemplate = redisTemplate;
        this.skipPaths = skipPathsCsv == null ? new String[0] : skipPathsCsv.split("\\s*,\\s*");
        this.secret = secret;
        this.requiredScope = requiredScope == null || requiredScope.isBlank() ? null : requiredScope;
        byte[] bytes = this.secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * @return JwtClaims with clientId, userId, scopes, and raw token; or null if
     *         invalid
     */
    public JwtClaims validateAndExtract(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank())
            return null;
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken;

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            Claims c = jws.getPayload();
            String userId = c.getSubject();
            String clientId = c.get("client_id", String.class);
            if (clientId == null)
                clientId = c.get("clientId", String.class);

            List<String> scopes = parseScope(c);

            if (requiredScope != null && (scopes == null || !scopes.contains(requiredScope))) {
                return null;
            }

            return new JwtClaims(
                    userId,
                    clientId,
                    scopes != null ? scopes : Collections.emptyList(),
                    token);
        } catch (JwtException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseScope(Claims c) {
        Object s = c.get("scope");
        if (s == null)
            return null;
        if (s instanceof String) {
            String str = (String) s;
            if (str.isBlank())
                return Collections.emptyList();
            return List.of(str.trim().split("\\s+"));
        }
        if (s instanceof List) {
            return ((List<?>) s).stream().map(String::valueOf).collect(Collectors.toList());
        }
        return null;
    }

    public static final class JwtClaims {
        private final String userId;
        private final String clientId;
        private final List<String> scopes;
        private final String rawToken;

        public JwtClaims(String userId, String clientId, List<String> scopes, String rawToken) {
            this.userId = userId;
            this.clientId = clientId;
            this.scopes = scopes;
            this.rawToken = rawToken;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public String getRawToken() {
            return rawToken;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String p : skipPaths) {
            if (p != null && !p.isEmpty() && path.startsWith(p.trim()))
                return true;
        }
        return false;
    }

    private static final String DEFAULT_CLIENT_ID = "dev-client";
    private static final String DEFAULT_USER_ID = "dev-user";
    private static final String DEFAULT_SCOPE = "agent:invoke";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String auth = request.getHeader("Authorization");
            JwtClaims claims = validateAndExtract(auth);

            String clientId;
            String userId;
            List<String> scopes;
            String rawToken;

            if (claims != null && claims.getClientId() != null && !claims.getClientId().isBlank()) {
                clientId = claims.getClientId();
                userId = claims.getUserId();
                scopes = claims.getScopes() != null ? claims.getScopes() : Collections.emptyList();
                rawToken = claims.getRawToken();
            } else {
                clientId = DEFAULT_CLIENT_ID;
                userId = DEFAULT_USER_ID;
                scopes = List.of(DEFAULT_SCOPE);
                rawToken = null;
            }

            String sessionId = request.getHeader("X-Session-Id");
            String resolvedSessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId : "default-" + clientId;
            AgentSessionEntity sessionEntity = sessionService.findBySessionIdAndClientId(resolvedSessionId, clientId)
                    .orElseGet(() -> sessionService.createOrGet(resolvedSessionId, clientId, userId,
                            String.join(" ", scopes)));

            String domainJson = domainContentService.loadByClientId(clientId);

            AgentContext ctx = new AgentContext();
            ctx.setSession(sessionEntity.toSession());
            ctx.setRoles(scopes.stream().collect(Collectors.toSet()));
            ctx.setAuthToken(rawToken);
            ctx.setSource(clientId);
            ctx.setCorrelationId(request.getHeader("X-Correlation-Id"));
            ctx.setDomainContentJson(domainJson);

            AgentContextHolder.setContext(ctx);

            String requestUri = request.getRequestURI();
            if (DEFAULT_CLIENT_ID.equals(clientId)) {
                if (!requestUri.startsWith("/api/trial")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"access_denied\", \"message\":\"Default session can only access Trial Agent\"}");
                    return;
                }
            } else {
                if (requestUri.startsWith("/api/admin/") && !scopes.contains("admin")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"insufficient_scope\", \"message\":\"Admin privileges required\"}");
                    return;
                }
            }

            // --- IDEMPOTENCY LAYER ---
            String idempotencyKey = request.getHeader("X-Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                idempotencyKey = request.getHeader("Idempotency-Key");
            }

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String redisKey = "idempotency:" + clientId + ":" + idempotencyKey;
                boolean lockAcquired = false;
                
                for (int i = 0; i < idempotencyRetryCount; i++) {
                    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotencyExpirySeconds));
                    if (Boolean.TRUE.equals(isNew)) {
                        lockAcquired = true;
                        break;
                    } else {
                        String status = redisTemplate.opsForValue().get(redisKey);
                        if ("COMPLETED".equals(status)) {
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Duplicate request detected (Idempotency Key).\"}");
                            return;
                        }
                        try {
                            Thread.sleep(idempotencyRetryIntervalMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                if (!lockAcquired) {
                    String status = redisTemplate.opsForValue().get(redisKey);
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    response.setContentType("application/json");
                    if ("COMPLETED".equals(status)) {
                        response.getWriter().write("{\"error\":\"Duplicate request detected (Idempotency Key).\"}");
                    } else {
                        response.getWriter().write("{\"error\":\"Concurrent duplicate request is still processing.\"}");
                    }
                    return;
                }
                
                request.setAttribute("Idempotency-Redis-Key", redisKey);
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"auth_error\"}");
            return;
        }

        try {
            filterChain.doFilter(request, response);
            String redisKey = (String) request.getAttribute("Idempotency-Redis-Key");
            if (redisKey != null) {
                redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofSeconds(idempotencyExpirySeconds));
            }
        } catch (Exception filterError) {
            String redisKey = (String) request.getAttribute("Idempotency-Redis-Key");
            if (redisKey != null) {
                redisTemplate.delete(redisKey); // clean state for retry
            }
            throw filterError;
        } finally {
            AgentContextHolder.clear();
        }
    }
}
