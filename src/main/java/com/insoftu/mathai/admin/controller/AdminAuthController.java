package com.insoftu.mathai.admin.controller;

import com.insoftu.mathai.admin.dto.LoginRequest;
import com.insoftu.mathai.admin.dto.LoginResponse;
import com.insoftu.mathai.admin.dto.TwoFactorRequest;
import com.insoftu.mathai.admin.security.JwtUtil;
import com.insoftu.mathai.admin.service.AdminAuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final JwtUtil jwtUtil;

    public AdminAuthController(AdminAuthService adminAuthService, JwtUtil jwtUtil) {
        this.adminAuthService = adminAuthService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = adminAuthService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<LoginResponse> verifyTwoFactor(@RequestBody TwoFactorRequest request) {
        LoginResponse response = adminAuthService.verifyTwoFactor(
                request.email(), request.code(), request.tempToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<Map<String, String>> setupTwoFactor(HttpServletRequest request) {
        UUID adminUserId = (UUID) request.getAttribute("adminUserId");
        String email = (String) request.getAttribute("adminEmail");
        String qrDataUri = adminAuthService.beginTwoFactorSetup(adminUserId, email);
        return ResponseEntity.ok(Map.of("qrCodeUri", qrDataUri));
    }

    @PostMapping("/2fa/verify-setup")
    public ResponseEntity<Map<String, String>> verifyTwoFactorSetup(
            @RequestBody TwoFactorRequest request,
            HttpServletRequest httpRequest) {
        UUID adminUserId = (UUID) httpRequest.getAttribute("adminUserId");
        adminAuthService.verifyAndEnableTwoFactor(adminUserId, request.code());
        return ResponseEntity.ok(Map.of("message", "2FA enabled successfully"));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Map<String, String>> disableTwoFactor(HttpServletRequest request) {
        UUID adminUserId = (UUID) request.getAttribute("adminUserId");
        adminAuthService.disableTwoFactor(adminUserId);
        return ResponseEntity.ok(Map.of("message", "2FA disabled successfully"));
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<Map<String, Object>> twoFactorStatus(HttpServletRequest request) {
        UUID adminUserId = (UUID) request.getAttribute("adminUserId");
        boolean enabled = adminAuthService.isTwoFactorEnabled(adminUserId);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing token"));
        }

        String token = authHeader.substring(7);
        Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }

        String refreshed = jwtUtil.refreshToken(claims);
        return ResponseEntity.ok(Map.of("token", refreshed));
    }
}
