package com.notify.agent.controllers.admin;

import com.notify.agent.AdminUserRepository;
import com.notify.agent.AgentContextHolder;
import com.notify.agent.ClientRepository;
import com.notify.agent.models.AdminUser;
import com.notify.agent.models.ClientEntity;
import com.notify.agent.service.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier verifier;

    public AdminAuthController(
            AdminUserRepository adminUserRepository,
            ClientRepository clientRepository,
            JwtService jwtService,
            @Value("${acp.auth.google.client-id:default-google-client-id}") String googleClientId) {
        this.adminUserRepository = adminUserRepository;
        this.clientRepository = clientRepository;
        this.jwtService = jwtService;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    public static class GoogleLoginRequest {
        public String idToken;
    }

    public static class LogoutRequest {
        public String accessToken;
        public String refreshToken;
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            GoogleIdToken idToken = verifier.verify(request.idToken);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String userId = payload.getSubject();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                AdminUser adminUser = adminUserRepository.findByGoogleUserId(userId).orElseGet(() -> {
                    AdminUser newUser = new AdminUser();
                    newUser.setGoogleUserId(userId);
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPictureUrl(pictureUrl);
                    newUser.setTenantId("t-" + UUID.randomUUID().toString()); // Auto-provision tenant
                    return adminUserRepository.save(newUser);
                });

                adminUser.setLastLogin(LocalDateTime.now());
                adminUserRepository.save(adminUser);

                String accessToken = jwtService.generateAccessToken(adminUser.getGoogleUserId(),
                        adminUser.getTenantId(), Map.of("email", email, "scope", "admin"));
                String refreshToken = jwtService.generateRefreshToken(adminUser.getGoogleUserId(),
                        adminUser.getTenantId());

                jwtService.storeTokens(adminUser.getGoogleUserId(), accessToken, refreshToken);

                return ResponseEntity.ok(Map.of(
                        "accessToken", accessToken,
                        "refreshToken", refreshToken,
                        "tenantId", adminUser.getTenantId(),
                        "email", email,
                        "name", name));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid ID token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest request) {
        jwtService.invalidateTokens(request.accessToken, request.refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private final ClientRepository clientRepository;

    public static class GenerateClientRequest {
        public String applicationName;
        public String basePackage;
        public int validityDays;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateClient(@RequestBody GenerateClientRequest request) {
        String tenantId = AgentContextHolder.getContext().getTenantId();

        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Tenant context missing or invalid."));
        }

        ClientEntity clientEntity = new ClientEntity();
        clientEntity.setClientId("cli_" + UUID.randomUUID().toString().replace("-", ""));
        clientEntity.setClientSecret(UUID.randomUUID().toString()); // Optional secret
        clientEntity.setApplicationName(request.applicationName);
        clientEntity.setBasePackage(request.basePackage);

        if (request.validityDays > 0) {
            clientEntity.setExpiresAt(LocalDateTime.now().plusDays(request.validityDays));
        }

        clientRepository.save(clientEntity);

        return ResponseEntity.ok(Map.of(
                "clientId", clientEntity.getClientId(),
                "applicationName", clientEntity.getApplicationName(),
                "tenantId", tenantId,
                "expiresAt", clientEntity.getExpiresAt() != null ? clientEntity.getExpiresAt().toString() : "NEVER"));
    }
}
