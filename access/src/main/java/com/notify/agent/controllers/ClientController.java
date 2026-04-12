package com.notify.agent.controllers;

import com.notify.agent.models.ClientRegistrationDto;
import com.notify.agent.models.TokenRefreshDto;
import com.notify.agent.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for client-facing operations: registration and auth.
 */
@RestController
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * POST /api/client/register
     * Register a new application and return a JWT token.
     */
    @PostMapping("/client/register")
    public ResponseEntity<ClientRegistrationDto.Response> registerClient(
            @RequestBody ClientRegistrationDto.Request request) {
        return ResponseEntity.ok(clientService.register(request));
    }

    /**
     * POST /api/auth/token/refresh
     * Refresh a JWT token using a refresh token.
     */
    @PostMapping("/auth/token/refresh")
    public ResponseEntity<TokenRefreshDto.Response> refreshToken(
            @RequestBody TokenRefreshDto.Request request) {
        return ResponseEntity.ok(clientService.refreshToken(request));
    }
}
