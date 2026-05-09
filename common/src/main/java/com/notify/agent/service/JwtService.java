package com.notify.agent.service;

import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.annotations.ManagedConfiguration.ConfigSource;
import com.notify.agent.interfaces.TokenStore;
import com.notify.agent.interfaces.TokenValidator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core JWT service — signs, verifies, and extracts claims from JWTs.
 *
 * <p>
 * Token <em>storage</em> and <em>active-validation</em> are delegated to
 * {@link TokenStore} and {@link TokenValidator} respectively, so this class
 * carries no Redis or other infrastructure dependency.
 * </p>
 *
 * <p>
 * Implementations of those interfaces are registered as Spring beans in
 * each consuming module (e.g. {@code RedisTokenStore} in acp-server,
 * {@code TokenHolder} in the client SDK).
 * </p>
 */
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
    private final TokenStore tokenStore;
    private final TokenValidator tokenValidator;

    public JwtService(
            @Value("${acp.auth.jwt.secret:wsws}") String secret,
            @Value("${acp.auth.jwt.required-scope:agent:invoke}") String requiredScope,
            TokenStore tokenStore,
            TokenValidator tokenValidator) {
        this.secret = secret;
        this.requiredScope = requiredScope == null || requiredScope.isBlank() ? null : requiredScope;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenStore = tokenStore;
        this.tokenValidator = tokenValidator;
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    public String generateAccessToken(String userId, String tenantId, Map<String, Object> extraClaims) {
        return generateToken(userId, tenantId, extraClaims, accessTokenExpirationSeconds * 1000);
    }

    public String generateRefreshToken(String userId, String tenantId) {
        return generateToken(userId, tenantId, Map.of("type", "refresh"), refreshTokenExpirationSeconds * 1000);
    }

    /**
     * Generates a token with no expiry for embedding in Kafka message headers.
     * The signature still ensures authenticity — expiry is not meaningful for
     * async message-passing contexts.
     */
    public String generateKafkaHeaderToken(String userId, String tenantId) {
        return Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("client_id", userId) // matches the clientId check in KafkaNotifyConsumer
                .claim("scope", "agent:invoke")
                .claim("kafka_token", true) // marker — lets callers detect header tokens
                .issuedAt(new Date())
                // intentionally NO .expiration() call
                .signWith(getSigningKey())
                .compact();
    }

    // -------------------------------------------------------------------------
    // Token validation / extraction
    // -------------------------------------------------------------------------

    /**
     * Validates signature + expiry and extracts claims.
     *
     * @return {@link JwtClaims} if the token is valid and meets scope requirements;
     *         {@code null} otherwise
     */
    public JwtClaims validateAndExtract(String bearerToken) {
        return parseToken(bearerToken, 0);
    }

    /**
     * Like {@link #validateAndExtract} but allows tokens that expired within
     * {@code clockSkewSeconds} seconds. Use only for Kafka message-header tokens.
     */
    public JwtClaims validateAndExtractKafkaHeader(String bearerToken, long clockSkewSeconds) {
        return parseToken(bearerToken, clockSkewSeconds);
    }

    // -------------------------------------------------------------------------
    // Token store delegation
    // -------------------------------------------------------------------------

    /**
     * Stores an access + refresh token pair via the injected {@link TokenStore}.
     */
    public void storeTokens(String userId, String accessToken, String refreshToken) {
        tokenStore.storeTokens(userId, accessToken, refreshToken,
                accessTokenExpirationSeconds, refreshTokenExpirationSeconds);
    }

    /**
     * Revokes both tokens via the injected {@link TokenStore}.
     */
    public void invalidateTokens(String accessToken, String refreshToken) {
        tokenStore.invalidateTokens(accessToken, refreshToken);
    }

    // -------------------------------------------------------------------------
    // Token validator delegation
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the access token is still active in the store. */
    public boolean validateAccessToken(String accessToken) {
        return tokenValidator.isAccessTokenValid(accessToken);
    }

    /** Returns {@code true} if the refresh token is still active in the store. */
    public boolean validateRefreshToken(String refreshToken) {
        return tokenValidator.isRefreshTokenValid(refreshToken);
    }

    // -------------------------------------------------------------------------
    // Claim extraction helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // JwtClaims value object
    // -------------------------------------------------------------------------

    public static final class JwtClaims {
        private final String userId;
        private final String clientId;
        private final String tenantId;
        private final List<String> scopes;
        private final String rawToken;

        public JwtClaims(String userId, String clientId, String tenantId,
                List<String> scopes, String rawToken) {
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private JwtClaims parseToken(String bearerToken, long clockSkewSeconds) {
        if (bearerToken == null || bearerToken.isBlank())
            return null;
        String token = bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7).trim()
                : bearerToken;

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(clockSkewSeconds)
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

            return new JwtClaims(userId, clientId, tenantId,
                    scopes != null ? scopes : Collections.emptyList(), token);
        } catch (JwtException e) {
            return null;
        }
    }

    private String generateToken(String userId, String tenantId,
            Map<String, Object> extraClaims, long expirationMillis) {
        Map<String, Object> claims = new java.util.HashMap<>();
        if (extraClaims != null)
            claims.putAll(extraClaims);

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .claim("tenantId", tenantId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey())
                .compact();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseScope(Claims c) {
        Object s = c.get("scope");
        if (s == null)
            return null;
        if (s instanceof String str) {
            return str.isBlank() ? Collections.emptyList()
                    : List.of(str.trim().split("\\s+"));
        }
        if (s instanceof List) {
            return ((List<?>) s).stream().map(String::valueOf).collect(Collectors.toList());
        }
        return null;
    }
}
