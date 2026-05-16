package com.insoftu.mathai.admin.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_totp")
public class AdminTotp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Column(nullable = false)
    private String secret;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AdminTotp() {}

    public AdminTotp(UUID adminUserId, String secret) {
        this.adminUserId = adminUserId;
        this.secret = secret;
        this.enabled = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAdminUserId() { return adminUserId; }
    public String getSecret() { return secret; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setSecret(String secret) { this.secret = secret; }
}
