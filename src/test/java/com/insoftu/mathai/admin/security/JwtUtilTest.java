package com.insoftu.mathai.admin.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final UUID userId = UUID.randomUUID();
    private final String email = "admin@example.com";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret", 30);
    }

    @Test
    void shouldCreateAndValidateToken() {
        String token = jwtUtil.createToken(userId, email);
        assertNotNull(token);

        Claims claims = jwtUtil.validateToken(token);
        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(email, claims.get("email", String.class));
        assertNotNull(claims.get("lastActivity", Long.class));
    }

    @Test
    void shouldRejectInvalidToken() {
        Claims claims = jwtUtil.validateToken("invalid-token");
        assertNull(claims);
    }

    @Test
    void shouldRejectNullToken() {
        Claims claims = jwtUtil.validateToken(null);
        assertNull(claims);
    }

    @Test
    void shouldRejectEmptyToken() {
        Claims claims = jwtUtil.validateToken("");
        assertNull(claims);
    }

    @Test
    void shouldNotRefreshRecentlyCreatedToken() {
        String token = jwtUtil.createToken(userId, email);
        Claims claims = jwtUtil.validateToken(token);
        assertNotNull(claims);

        // Token just created should not need refresh
        assertFalse(jwtUtil.shouldRefresh(claims));
    }

    @Test
    void shouldRefreshToken() {
        String token = jwtUtil.createToken(userId, email);
        Claims claims = jwtUtil.validateToken(token);
        assertNotNull(claims);

        String refreshed = jwtUtil.refreshToken(claims);
        assertNotNull(refreshed);

        // Refreshed token should be valid
        Claims newClaims = jwtUtil.validateToken(refreshed);
        assertNotNull(newClaims);
        assertEquals(userId.toString(), newClaims.getSubject());
    }

    @Test
    void shouldHandleShortSecretsByBase64() {
        // Should not throw — short secrets get Base64 encoded into a 256-bit key
        JwtUtil shortSecretUtil = new JwtUtil("short", 30);
        assertNotNull(shortSecretUtil);
    }
}
