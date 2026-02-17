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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * Authentication filter that: validates JWT, verifies client id and scope,
 * creates AgentContext, loads domain content and session history from the database,
 * and sets AgentContextHolder. On failure returns 401.
 */
@Component
@Order(1)
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final DomainContentService domainContentService;
    private final String[] skipPaths;

    @Value("${acp.auth.jwt.secret:wsws}") String secret = "secretmdkemdokp4i98985908606805609706ktk0509i05968905087096870698";
    @Value("${acp.auth.jwt.required-scope:agent:invoke}") String requiredScope="invoke";

    public AgentAuthenticationFilter(
                                        SessionService sessionService,
                                     DomainContentService domainContentService,
                                     @Value("${acp.auth.skip-paths:/api/client/register,/api/auth/token/refresh,/actuator/health,/actuator/info}") String skipPathsCsv) {
        this.sessionService = sessionService;
        this.domainContentService = domainContentService;
        this.skipPaths = skipPathsCsv == null ? new String[0] : skipPathsCsv.split("\\s*,\\s*");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.requiredScope = requiredScope == null || requiredScope.isBlank() ? null : requiredScope;
    }

    private final SecretKey key;
    /**
     * @return JwtClaims with clientId, userId, scopes, and raw token; or null if invalid
     */
    public JwtClaims validateAndExtract(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return null;
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken;

        try {
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

            Claims c = jws.getPayload();
            String userId = c.getSubject();
            String clientId = c.get("client_id", String.class);
            if (clientId == null) clientId = c.get("clientId", String.class);

            List<String> scopes = parseScope(c);

            if (requiredScope != null && (scopes == null || !scopes.contains(requiredScope))) {
                return null;
            }

            return new JwtClaims(
                userId,
                clientId,
                scopes != null ? scopes : Collections.emptyList(),
                token
            );
        } catch (JwtException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseScope(Claims c) {
        Object s = c.get("scope");
        if (s == null) return null;
        if (s instanceof String) {
            String str = (String) s;
            if (str.isBlank()) return Collections.emptyList();
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

        public String getUserId() { return userId; }
        public String getClientId() { return clientId; }
        public List<String> getScopes() { return scopes; }
        public String getRawToken() { return rawToken; }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String p : skipPaths) {
            if (p != null && !p.isEmpty() && path.startsWith(p.trim())) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            try {
                filterChain.doFilter(request, response);
            } finally {
                AgentContextHolder.clear();
            }
            String auth = request.getHeader("Authorization");
            JwtClaims claims = validateAndExtract(auth);

            if (claims == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid_or_missing_token\"}");
                return;
            }

            if (claims.getClientId() == null || claims.getClientId().isBlank()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"client_id_required\"}");
                return;
            }

            String sessionId = request.getHeader("X-Session-Id");
            String resolvedSessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId : "default-" + claims.getClientId();
            AgentSessionEntity sessionEntity = sessionService.findBySessionIdAndClientId(resolvedSessionId, claims.getClientId())
                .orElseGet(() -> sessionService.createOrGet(resolvedSessionId, claims.getClientId(), claims.getUserId(), String.join(" ", claims.getScopes())));

            String domainJson = domainContentService.loadByClientId(claims.getClientId());

            AgentContext ctx = new AgentContext();
            ctx.setSession(sessionEntity.toSession());
            ctx.setRoles(claims.getScopes() == null ? Set.of() : claims.getScopes().stream().collect(Collectors.toSet()));
            ctx.setAuthToken(claims.getRawToken());
            ctx.setSource(claims.getClientId());
            ctx.setCorrelationId(request.getHeader("X-Correlation-Id"));
            ctx.setDomainContentJson(domainJson);

            AgentContextHolder.setContext(ctx);

            try {
                filterChain.doFilter(request, response);
            } finally {
                AgentContextHolder.clear();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"auth_error\"}");
        }
    }
}
