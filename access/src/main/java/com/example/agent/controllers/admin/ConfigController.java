package com.example.agent.controllers.admin;

import com.example.agent.service.ManagedConfigService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class ConfigController {

    private final ManagedConfigService managedConfigService;

    public ConfigController(ManagedConfigService managedConfigService) {
        this.managedConfigService = managedConfigService;
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, String>> setConfig(@RequestBody Map<String, Object> payload) {
        try {
            managedConfigService.updateConfigMap(payload);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to update configuration"));
        }
        return ResponseEntity.ok(Map.of("message", "Configuration dynamically updated"));
    }

}
