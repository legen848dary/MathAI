package com.insoftu.mathai.admin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSettings {

    @Id
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppSettings() {}

    public AppSettings(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
