package com.notify.agent;

import com.notify.agent.models.ClientEntity;
import com.notify.agent.models.ClientRegistrationDto;
import com.notify.agent.models.TokenRefreshDto;
import com.notify.agent.service.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for client registration, management, and token issuance.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final JwtService jwtService;
    private final long tokenExpiryMs;
    private final long refreshExpiryMs;

    public ClientService(
            ClientRepository clientRepository,
            JwtService jwtService,
            @Value("${acp.auth.jwt.expiry-ms:3600000}") long tokenExpiryMs,
            @Value("${acp.auth.jwt.refresh-expiry-ms:604800000}") long refreshExpiryMs) {
        this.clientRepository = clientRepository;
        this.jwtService = jwtService;
        this.tokenExpiryMs = tokenExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    /**
     * Register a new client or update an existing one.
     * Generates a new JWT and refresh token.
     */
    @Transactional
    public ClientRegistrationDto.Response register(ClientRegistrationDto.Request request) {
        ClientEntity client = clientRepository.findByClientId(request.getClientId())
                .orElseThrow(() -> new RuntimeException(
                        "Invalid or pre-registered API key strictly required for registration. Client ID: "
                                + request.getClientId()));

        client.setApplicationName(request.getApplicationName());
        client.setBasePackage(request.getBasePackage());
        clientRepository.save(client);

        String token = jwtService.generateAccessToken(client.getClientId(), client.getTenantId(), null);
        String refreshToken = jwtService.generateRefreshToken(client.getClientId(), client.getTenantId());

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

        String newToken = jwtService.generateAccessToken(client.get().getClientId(), client.get().getTenantId(), null);

        TokenRefreshDto.Response response = new TokenRefreshDto.Response();
        response.setToken(newToken);
        response.setExpiresInMs(tokenExpiryMs);

        return response;
    }

}
