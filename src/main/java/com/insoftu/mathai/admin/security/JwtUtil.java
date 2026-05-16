package com.insoftu.mathai.admin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtUtil(@Value("${admin.jwt.secret:mathai-jwt-secret-change-me}") String secret,
                   @Value("${admin.jwt.expiration-minutes:30}") long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
        // Derive a 256-bit key from the secret
        byte[] keyBytes = secret.getBytes();
        if (keyBytes.length < 32) {
            // Pad with SHA-256 hash of the secret to get a strong 32-byte key
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = Arrays.copyOf(digest.digest(keyBytes), 32);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        } else {
            // If secret looks like Base64, decode it; otherwise hash for consistency
            try {
                keyBytes = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException e) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    keyBytes = Arrays.copyOf(digest.digest(keyBytes), 32);
                } catch (NoSuchAlgorithmException ex) {
                    throw new RuntimeException("SHA-256 not available", ex);
                }
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(UUID adminUserId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);

        return Jwts.builder()
                .subject(adminUserId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("lastActivity", now.getEpochSecond())
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a token and returns its claims. Returns null if invalid/expired.
     */
    public Claims validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether a token should be refreshed based on last activity.
     * Returns true if the token is valid and we should issue a new one
     * (activity occurred, extending the session).
     */
    public boolean shouldRefresh(Claims claims) {
        Instant now = Instant.now();
        Instant lastActivity = Instant.ofEpochSecond(
                claims.get("lastActivity", Long.class));
        // Refresh if more than 1 minute since last activity
        return now.minusSeconds(60).isAfter(lastActivity);
    }

    /**
     * Refreshes a token — same subject/email, new issued-at, new expiry,
     * new lastActivity timestamp.
     */
    public String refreshToken(Claims claims) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);

        return Jwts.builder()
                .subject(claims.getSubject())
                .claim("email", claims.get("email", String.class))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("lastActivity", now.getEpochSecond())
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
