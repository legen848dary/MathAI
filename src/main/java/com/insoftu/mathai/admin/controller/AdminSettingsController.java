package com.insoftu.mathai.admin.controller;

import com.insoftu.mathai.admin.service.AdminSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    public AdminSettingsController(AdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSettings() {
        return ResponseEntity.ok(adminSettingsService.getAllSettings());
    }

    @PutMapping("/ai-provider")
    public ResponseEntity<Map<String, String>> setAiProvider(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        if (provider == null || provider.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider name is required"));
        }

        try {
            adminSettingsService.setAiProvider(provider);
            return ResponseEntity.ok(Map.of(
                    "message", "AI provider switched to " + provider,
                    "provider", provider
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> setSetting(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Key is required"));
        }

        adminSettingsService.setSetting(key, value);
        return ResponseEntity.ok(Map.of("message", "Setting updated"));
    }
}
