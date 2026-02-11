package com.blancocl.licensing.service;

import com.blancocl.licensing.api.LicenseValidationResult;
import com.blancocl.licensing.api.PluginLicenseService;
import com.blancocl.licensing.model.LicenseRecord;
import com.blancocl.licensing.remote.PanelLicenseClient;
import com.blancocl.licensing.remote.RemoteValidationResponse;
import com.blancocl.licensing.repository.LicenseRepository;
import com.blancocl.licensing.security.HmacLicenseSigner;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class HybridLicenseService implements PluginLicenseService {
    private final LicenseRepository repository;
    private final HmacLicenseSigner signer;
    private final LicenseMode mode;
    private final PanelLicenseClient panelClient;

    public HybridLicenseService(
            JavaPlugin plugin,
            LicenseRepository repository,
            HmacLicenseSigner signer,
            LicenseMode mode,
            PanelLicenseClient panelClient
    ) {
        this.repository = repository;
        this.signer = signer;
        this.mode = mode;
        this.panelClient = panelClient;
    }

    @Override
    public synchronized LicenseRecord issueLicense(String pluginId, String owner, int validDays) {
        String normalizedPluginId = normalizePluginId(pluginId);

        Instant now = Instant.now();
        Instant expiresAt = validDays <= 0 ? null : now.plus(validDays, ChronoUnit.DAYS);
        String key = signer.generate(normalizedPluginId);
        LicenseRecord localRecord = new LicenseRecord(key, normalizedPluginId, owner, now, expiresAt, false);

        repository.upsert(localRecord);

        if (panelClient != null && mode != LicenseMode.LOCAL) {
            Optional<LicenseRecord> panelRecord = panelClient.issue(normalizedPluginId, owner, validDays);
            panelRecord.ifPresent(repository::upsert);
            if (panelRecord.isPresent()) {
                return panelRecord.get();
            }
        }

        return localRecord;
    }

    @Override
    public LicenseValidationResult validate(String pluginId, String key) {
        String normalizedPluginId = normalizePluginId(pluginId);

        if (!signer.verify(normalizedPluginId, key)) {
            return LicenseValidationResult.SIGNATURE_INVALID;
        }

        if (mode == LicenseMode.LOCAL || panelClient == null) {
            return validateLocal(normalizedPluginId, key);
        }

        RemoteValidationResponse remote = panelClient.validate(normalizedPluginId, key);
        if (remote.result() == LicenseValidationResult.REMOTE_ERROR) {
            return mode == LicenseMode.HYBRID
                    ? validateLocal(normalizedPluginId, key)
                    : LicenseValidationResult.REMOTE_ERROR;
        }

        if (remote.record() != null) {
            repository.upsert(remote.record());
        }
        return remote.result();
    }

    @Override
    public synchronized boolean revoke(String key) {
        boolean localRevoked = repository.revoke(key);

        if (panelClient == null || mode == LicenseMode.LOCAL) {
            return localRevoked;
        }

        boolean remoteRevoked = panelClient.revoke(key);
        if (mode == LicenseMode.REMOTE) {
            return remoteRevoked;
        }

        return remoteRevoked || localRevoked;
    }

    @Override
    public Optional<LicenseRecord> get(String key) {
        if (panelClient != null && mode != LicenseMode.LOCAL) {
            Optional<LicenseRecord> remote = panelClient.get(key);
            if (remote.isPresent()) {
                repository.upsert(remote.get());
                return remote;
            }
            if (mode == LicenseMode.REMOTE) {
                return Optional.empty();
            }
        }

        return repository.find(key);
    }

    private LicenseValidationResult validateLocal(String pluginId, String key) {
        Optional<LicenseRecord> record = repository.find(key);
        if (record.isEmpty()) {
            return LicenseValidationResult.NOT_FOUND;
        }

        LicenseRecord value = record.get();
        if (!value.pluginId().equalsIgnoreCase(pluginId)) {
            return LicenseValidationResult.WRONG_PLUGIN;
        }
        if (value.revoked()) {
            return LicenseValidationResult.REVOKED;
        }
        if (value.isExpired(Instant.now())) {
            return LicenseValidationResult.EXPIRED;
        }
        return LicenseValidationResult.VALID;
    }

    private String normalizePluginId(String pluginId) {
        return pluginId == null ? "" : pluginId.trim().toLowerCase();
    }
}
