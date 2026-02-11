package com.blancocl.licensing.repository;

import com.blancocl.licensing.model.LicenseRecord;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

public final class JdbcLicenseRepository implements LicenseRepository {
    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public JdbcLicenseRepository(JavaPlugin plugin, String jdbcUrl, String username, String password) {
        this.plugin = plugin;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        createTableIfNeeded();
    }

    @Override
    public synchronized void upsert(LicenseRecord record) {
        String sql = "INSERT INTO licenses (license_key, plugin_id, owner_name, issued_at, expires_at, revoked) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(license_key) DO UPDATE SET " +
                "plugin_id = excluded.plugin_id, " +
                "owner_name = excluded.owner_name, " +
                "issued_at = excluded.issued_at, " +
                "expires_at = excluded.expires_at, " +
                "revoked = excluded.revoked";

        boolean sqlite = jdbcUrl.startsWith("jdbc:sqlite:");
        if (!sqlite) {
            sql = "INSERT INTO licenses (license_key, plugin_id, owner_name, issued_at, expires_at, revoked) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE plugin_id = VALUES(plugin_id), owner_name = VALUES(owner_name), " +
                    "issued_at = VALUES(issued_at), expires_at = VALUES(expires_at), revoked = VALUES(revoked)";
        }

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, record.key());
            ps.setString(2, record.pluginId());
            ps.setString(3, record.owner());
            ps.setLong(4, record.issuedAt().getEpochSecond());
            if (record.expiresAt() == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, record.expiresAt().getEpochSecond());
            }
            ps.setBoolean(6, record.revoked());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to upsert license: " + e.getMessage());
        }
    }

    @Override
    public Optional<LicenseRecord> find(String key) {
        String sql = "SELECT license_key, plugin_id, owner_name, issued_at, expires_at, revoked FROM licenses WHERE license_key = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRecord(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to query license: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized boolean revoke(String key) {
        String sql = "UPDATE licenses SET revoked = ? WHERE license_key = ? AND revoked = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, true);
            ps.setString(2, key);
            ps.setBoolean(3, false);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to revoke license: " + e.getMessage());
            return false;
        }
    }

    private LicenseRecord mapRecord(ResultSet rs) throws SQLException {
        String key = rs.getString("license_key");
        String pluginId = rs.getString("plugin_id");
        String owner = rs.getString("owner_name");
        Instant issuedAt = Instant.ofEpochSecond(rs.getLong("issued_at"));
        long expiresEpoch = rs.getLong("expires_at");
        Instant expiresAt = rs.wasNull() ? null : Instant.ofEpochSecond(expiresEpoch);
        boolean revoked = rs.getBoolean("revoked");
        return new LicenseRecord(key, pluginId, owner, issuedAt, expiresAt, revoked);
    }

    private void createTableIfNeeded() {
        String sql = "CREATE TABLE IF NOT EXISTS licenses (" +
                "license_key VARCHAR(128) PRIMARY KEY," +
                "plugin_id VARCHAR(64) NOT NULL," +
                "owner_name VARCHAR(128) NOT NULL," +
                "issued_at BIGINT NOT NULL," +
                "expires_at BIGINT NULL," +
                "revoked BOOLEAN NOT NULL DEFAULT FALSE" +
                ")";

        try (Connection connection = getConnection();
             Statement st = connection.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create licenses table: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        if (username == null || username.isBlank()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }
}
