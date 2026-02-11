package com.blancocl.licensing.repository;

import com.blancocl.licensing.model.LicenseRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class YamlLicenseRepository implements LicenseRepository {
    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<String, LicenseRecord> licenses = new ConcurrentHashMap<>();

    public YamlLicenseRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "licenses.yml");
        load();
    }

    @Override
    public synchronized void upsert(LicenseRecord record) {
        licenses.put(record.key(), record);
        save();
    }

    @Override
    public Optional<LicenseRecord> find(String key) {
        return Optional.ofNullable(licenses.get(key));
    }

    @Override
    public synchronized boolean revoke(String key) {
        LicenseRecord existing = licenses.get(key);
        if (existing == null || existing.revoked()) {
            return false;
        }
        licenses.put(key, existing.withRevoked(true));
        save();
        return true;
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection section = yaml.getConfigurationSection("licenses");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null) {
                continue;
            }

            String pluginId = node.getString("pluginId", "unknown");
            String owner = node.getString("owner", "unknown");
            long issuedEpoch = node.getLong("issuedAt", Instant.now().getEpochSecond());
            long expiresEpoch = node.getLong("expiresAt", -1L);
            boolean revoked = node.getBoolean("revoked", false);

            Instant issuedAt = Instant.ofEpochSecond(issuedEpoch);
            Instant expiresAt = expiresEpoch < 0 ? null : Instant.ofEpochSecond(expiresEpoch);

            licenses.put(key, new LicenseRecord(key, pluginId, owner, issuedAt, expiresAt, revoked));
        }
    }

    private void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("licenses");

        for (LicenseRecord record : licenses.values()) {
            ConfigurationSection node = section.createSection(record.key());
            node.set("pluginId", record.pluginId());
            node.set("owner", record.owner());
            node.set("issuedAt", record.issuedAt().getEpochSecond());
            node.set("expiresAt", record.expiresAt() == null ? -1L : record.expiresAt().getEpochSecond());
            node.set("revoked", record.revoked());
        }

        try {
            yaml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save licenses.yml: " + e.getMessage());
        }
    }
}
