package com.mindcart.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One Expo push token per device per user.
 *
 * The token itself is the primary key rather than a surrogate id: Expo
 * tokens are globally unique, and a device handed to a different user must
 * MOVE to that user's row rather than create a duplicate -- otherwise the
 * previous owner keeps receiving pushes on a phone that is no longer
 * theirs. Re-registering the same token is therefore an upsert, which is
 * exactly what the client does on every cold start.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @Column(name = "token", length = 255)
    private String token;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "platform", length = 16)
    private String platform;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}