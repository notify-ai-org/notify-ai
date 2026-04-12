package com.notify.agent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${security.jwt.secret:defaultSecretKeyWhichShouldBeAtLeastThirtyTwoBytesLongForHS256}")
    private String secret;

    @Value("${security.jwt.access-token.expiration:3600}") // 1 hr
    private long accessTokenExpirationSeconds;

    @Value("${security.jwt.refresh-token.expiration:604800}") // 7 days
    private long refreshTokenExpirationSeconds;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(String userId, String tenantId, Map<String, Object> extraClaims) {
        return generateToken(userId, tenantId, extraClaims, accessTokenExpirationSeconds * 1000);
    }

    public String generateRefreshToken(String userId, String tenantId) {
        return generateToken(userId, tenantId, Map.of("type", "refresh"), refreshTokenExpirationSeconds * 1000);
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
