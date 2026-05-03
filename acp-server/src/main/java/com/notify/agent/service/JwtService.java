package com.notify.agent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.annotations.ManagedConfiguration.ConfigSource;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JwtService {

    @Value("${security.jwt.access-token.expiration:3600}") // 1 hr
    private long accessTokenExpirationSeconds;

    @Value("${security.jwt.refresh-token.expiration:604800}") // 7 days
    private long refreshTokenExpirationSeconds;

    @ManagedConfiguration(key = "acp.auth.jwt.secret", source = ConfigSource.CONFIG_MAP)
    String secret;

    @ManagedConfiguration(key = "acp.auth.jwt.required-scope", source = ConfigSource.CONFIG_MAP)
    String requiredScope;

    private final SecretKey key;

    public JwtService(
            @Value("${acp.auth.jwt.secret:wsws}") String secret,
            @Value("${acp.auth.jwt.required-scope:agent:invoke}") String requiredScope) {
        this.secret = secret;
        this.requiredScope = requiredScope == null || requiredScope.isBlank() ? null : requiredScope;
        byte[] bytes = this.secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(String userId, String tenantId, Map<String, Object> extraClaims) {
        return generateToken(userId, tenantId, extraClaims, accessTokenExpirationSeconds * 1000);
    }

    public String generateRefreshToken(String userId, String tenantId) {
        return generateToken(userId, tenantId, Map.of("type", "refresh"), refreshTokenExpirationSeconds * 1000);
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
            String tenantId = c.get("tenantId", String.class);

            List<String> scopes = parseScope(c);

            if (requiredScope != null && (scopes == null || !scopes.contains(requiredScope))) {
                return null;
            }

            return new JwtClaims(
                    userId,
                    clientId,
                    tenantId,
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
        private final String tenantId;
        private final List<String> scopes;
        private final String rawToken;

        public JwtClaims(String userId, String clientId, String tenantId, List<String> scopes, String rawToken) {
            this.userId = userId;
            this.clientId = clientId;
            this.tenantId = tenantId;
            this.scopes = scopes;
            this.rawToken = rawToken;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public String getRawToken() {
            return rawToken;
        }
    }

    private String generateToken(String userId, String tenantId, Map<String, Object> extraClaims,
            long expirationMillis) {
        Map<String, Object> claims = new java.util.HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .claim("tenantId", tenantId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractTenantId(String token) {
        return extractAllClaims(token).get("tenantId", String.class);
    }

    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;

    public void storeTokens(String userId, String accessToken, String refreshToken) {
        RedisCommands<String, String> sync = redisConnection.sync();

        // Store access token
        String accessKey = "auth:access:" + accessToken;
        sync.setex(accessKey, accessTokenExpirationSeconds, userId);

        // Store refresh token
        String refreshKey = "auth:refresh:" + refreshToken;
        sync.setex(refreshKey, refreshTokenExpirationSeconds, userId);
    }

    public boolean validateAccessToken(String accessToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        return sync.exists("auth:access:" + accessToken) > 0;
    }

    public boolean validateRefreshToken(String refreshToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        return sync.exists("auth:refresh:" + refreshToken) > 0;
    }

    public void invalidateTokens(String accessToken, String refreshToken) {
        RedisCommands<String, String> sync = redisConnection.sync();
        if (accessToken != null) {
            sync.del("auth:access:" + accessToken);
        }
        if (refreshToken != null) {
            sync.del("auth:refresh:" + refreshToken);
        }
    }
}
