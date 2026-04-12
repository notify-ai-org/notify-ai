package com.notify.agent.service;

import com.notify.agent.ClientRepository;
import com.notify.agent.models.ClientEntity;
import com.notify.agent.models.ClientRegistrationDto;
import com.notify.agent.models.TokenRefreshDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for client registration, management, and token issuance.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final SecretKey key;
    private final long tokenExpiryMs;
    private final long refreshExpiryMs;

    public ClientService(
            ClientRepository clientRepository,
            @Value("${acp.auth.jwt.secret:wsws}") String secret,
            @Value("${acp.auth.jwt.expiry-ms:3600000}") long tokenExpiryMs,
            @Value("${acp.auth.jwt.refresh-expiry-ms:604800000}") long refreshExpiryMs) {
        this.clientRepository = clientRepository;
        this.tokenExpiryMs = tokenExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * Register a new client or update an existing one.
     * Generates a new JWT and refresh token.
     */
    @Transactional
    public ClientRegistrationDto.Response register(ClientRegistrationDto.Request request) {
        ClientEntity client = clientRepository.findByClientId(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Invalid or pre-registered API key strictly required for registration. Client ID: " + request.getClientId()));

        client.setApplicationName(request.getApplicationName());
        client.setBasePackage(request.getBasePackage());
        clientRepository.save(client);

        String token = generateToken(client, tokenExpiryMs);
        String refreshToken = generateToken(client, refreshExpiryMs);

        ClientRegistrationDto.Response response = new ClientRegistrationDto.Response();
        response.setClientId(client.getClientId());
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setExpiresInMs(tokenExpiryMs);

        return response;
    }

    /**
     * Refresh an existing access token using a valid refresh token.
     */
    public TokenRefreshDto.Response refreshToken(TokenRefreshDto.Request request) {
        // Simple token refresh logic without deep validation for now
        // In product we'd validate the refresh token claim/expiry
        Optional<ClientEntity> client = clientRepository.findByClientId(request.getClientId());
        if (client.isEmpty()) {
            throw new RuntimeException("Invalid client ID");
        }

        String newToken = generateToken(client.get(), tokenExpiryMs);

        TokenRefreshDto.Response response = new TokenRefreshDto.Response();
        response.setToken(newToken);
        response.setExpiresInMs(tokenExpiryMs);

        return response;
    }

    private String generateToken(ClientEntity client, long expiryMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("client_id", client.getClientId());
        claims.put("scope", "agent:invoke"); // Default scope

        return Jwts.builder()
                .claims(claims)
                .subject(client.getApplicationName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }
}
