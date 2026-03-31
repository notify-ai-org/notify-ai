package com.example.agent.controllers.admin;

import com.example.agent.service.DomainContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class ConfigController {

    private final DomainContentService domainContentService;

    public ConfigController(DomainContentService domainContentService) {
        this.domainContentService = domainContentService;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        return ResponseEntity.ok(Map.of("key", key, "value", domainContentService.getConfig(key) != null ? domainContentService.getConfig(key) : "null"));
    }

    @PostMapping("/{key}")
    public ResponseEntity<Map<String, String>> setConfig(@PathVariable String key, @RequestBody Map<String, String> payload) {
        String value = payload.get("value");
        domainContentService.updateConfigMap(key, value);
        return ResponseEntity.ok(Map.of("message", "Configuration dynamically updated", "key", key, "value", value));
    }
}
