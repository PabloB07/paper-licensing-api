package com.blancocl.licensing.model;

import java.time.Instant;
import java.util.Objects;

public final class LicenseRecord {
    private final String key;
    private final String pluginId;
    private final String owner;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final boolean revoked;

    public LicenseRecord(
            String key,
            String pluginId,
            String owner,
            Instant issuedAt,
            Instant expiresAt,
            boolean revoked
    ) {
        this.key = key;
        this.pluginId = pluginId;
        this.owner = owner;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }

    public String key() {
        return key;
    }

    public String pluginId() {
        return pluginId;
    }

    public String owner() {
        return owner;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean revoked() {
        return revoked;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isValidFor(String requestedPluginId, Instant now) {
        if (!Objects.equals(pluginId, requestedPluginId)) {
            return false;
        }
        if (revoked) {
            return false;
        }
        return !isExpired(now);
    }

    public LicenseRecord withRevoked(boolean value) {
        return new LicenseRecord(key, pluginId, owner, issuedAt, expiresAt, value);
    }
}
