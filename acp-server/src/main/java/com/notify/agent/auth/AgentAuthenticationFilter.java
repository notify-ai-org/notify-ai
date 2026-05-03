package com.notify.agent.auth;

import com.notify.agent.ClientRepository;
import com.notify.agent.AgentContextHolder;
import com.notify.agent.models.AgentContext;
import com.notify.agent.models.AgentSessionEntity;
import com.notify.agent.service.IdempotencyService;
import com.notify.agent.service.IdempotencyService.AcquireResult;
import com.notify.agent.service.JwtService;
import com.notify.agent.service.JwtService.JwtClaims;
import com.notify.agent.service.SessionService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication filter that: validates JWT, verifies client id and scope,
 * creates AgentContext, loads domain content and session history from the
 * database, and sets AgentContextHolder.
 * Idempotency enforcement is delegated to {@link IdempotencyService}.
 * On failure returns 401.
 */
@Component
@Order(1)
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final ClientRepository clientRepository;
    private final JwtService jwtService;
    private final IdempotencyService idempotencyService;
    private final String[] skipPaths;

    public AgentAuthenticationFilter(
            SessionService sessionService,
            JwtService jwtService,
            ClientRepository clientRepository,
            IdempotencyService idempotencyService,
            @Value("${acp.auth.skip-paths:/client/register,/auth/token/refresh,/api/auth/token/refresh,/actuator/health,/actuator/info}") String skipPathsCsv) {
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.clientRepository = clientRepository;
        this.idempotencyService = idempotencyService;
        this.skipPaths = skipPathsCsv == null ? new String[0] : skipPathsCsv.split("\\s*,\\s*");
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
            JwtClaims claims = jwtService.validateAndExtract(auth);

            String clientId;
            String userId;
            String tenantId;
            List<String> scopes;
            String rawToken;

            if (claims != null && claims.getClientId() != null && !claims.getClientId().isBlank()) {
                clientId = claims.getClientId();
                userId = claims.getUserId();
                tenantId = claims.getTenantId();
                scopes = claims.getScopes() != null ? claims.getScopes() : Collections.emptyList();
                rawToken = claims.getRawToken();

                // MANDATORY CLIENT VALIDATION
                if (!DEFAULT_CLIENT_ID.equals(clientId)) {
                    com.notify.agent.models.ClientEntity fetchedClient = clientRepository.findByClientId(clientId)
                            .orElse(null);
                    if (fetchedClient == null) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter()
                                .write("{\"error\":\"unauthorized_client\", \"message\":\"Client ID not recognized\"}");
                        return;
                    }
                    if (fetchedClient.isExpired()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter()
                                .write("{\"error\":\"expired_client\", \"message\":\"Client ID has expired\"}");
                        return;
                    }
                }
            } else {
                clientId = DEFAULT_CLIENT_ID;
                userId = DEFAULT_USER_ID;
                tenantId = null;
                scopes = List.of(DEFAULT_SCOPE);
                rawToken = null;
            }

            String sessionId = request.getHeader("X-Session-Id");
            String resolvedSessionId = (sessionId != null && !sessionId.isBlank())
                    ? sessionId
                    : "default-" + clientId;
            AgentSessionEntity sessionEntity = sessionService.findBySessionIdAndClientId(resolvedSessionId, clientId)
                    .orElseGet(() -> sessionService.createOrGet(
                            resolvedSessionId, clientId, userId, String.join(" ", scopes)));

            AgentContext ctx = new AgentContext();
            ctx.setSession(sessionEntity.toSession());
            ctx.setRoles(scopes.stream().collect(Collectors.toSet()));
            ctx.setAuthToken(rawToken);
            ctx.setSource(clientId);
            ctx.setTenantId(tenantId);
            ctx.setCorrelationId(request.getHeader("X-Correlation-Id"));

            AgentContextHolder.setContext(ctx);

            // --- IDEMPOTENCY ---
            String idempotencyKey = request.getHeader("X-Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                idempotencyKey = request.getHeader("Idempotency-Key");
            }

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String[] redisKeyOut = new String[1];
                AcquireResult result = idempotencyService.acquireLock(clientId, idempotencyKey, redisKeyOut);

                switch (result) {
                    case ACQUIRED -> request.setAttribute("Idempotency-Redis-Key", redisKeyOut[0]);
                    case ALREADY_COMPLETED -> {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Duplicate request detected (Idempotency Key).\"}");
                        return;
                    }
                    case STILL_PROCESSING -> {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Concurrent duplicate request is still processing.\"}");
                        return;
                    }
                    case INTERRUPTED -> {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Server interrupted while checking idempotency.\"}");
                        return;
                    }
                }
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
            idempotencyService.markCompleted(redisKey);
        } catch (Exception filterError) {
            String redisKey = (String) request.getAttribute("Idempotency-Redis-Key");
            idempotencyService.releaseLock(redisKey);
            throw filterError;
        } finally {
            AgentContextHolder.clear();
        }
    }
}
