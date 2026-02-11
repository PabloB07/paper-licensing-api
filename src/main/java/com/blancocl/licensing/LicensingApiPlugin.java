package com.blancocl.licensing;

import com.blancocl.licensing.api.PluginLicenseService;
import com.blancocl.licensing.remote.HttpPanelLicenseClient;
import com.blancocl.licensing.remote.PanelLicenseClient;
import com.blancocl.licensing.repository.JdbcLicenseRepository;
import com.blancocl.licensing.repository.LicenseRepository;
import com.blancocl.licensing.repository.YamlLicenseRepository;
import com.blancocl.licensing.security.HmacLicenseSigner;
import com.blancocl.licensing.service.HybridLicenseService;
import com.blancocl.licensing.service.LicenseMode;
import com.blancocl.licensing.service.StorageType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LicensingApiPlugin extends JavaPlugin {
    private static final String ENV_SIGNING_SECRET = "PAPER_LIC_SIGNING_SECRET";
    private static final String ENV_MODE = "PAPER_LIC_MODE";
    private static final String ENV_STORAGE_TYPE = "PAPER_LIC_STORAGE_TYPE";
    private static final String ENV_SQLITE_FILE = "PAPER_LIC_SQLITE_FILE";
    private static final String ENV_MYSQL_JDBC_URL = "PAPER_LIC_MYSQL_JDBC_URL";
    private static final String ENV_MYSQL_USERNAME = "PAPER_LIC_MYSQL_USERNAME";
    private static final String ENV_MYSQL_PASSWORD = "PAPER_LIC_MYSQL_PASSWORD";
    private static final String ENV_PANEL_ENABLED = "PAPER_LIC_PANEL_ENABLED";
    private static final String ENV_PANEL_BASE_URL = "PAPER_LIC_PANEL_BASE_URL";
    private static final String ENV_PANEL_API_TOKEN = "PAPER_LIC_PANEL_API_TOKEN";
    private static final String ENV_PANEL_SERVER_ID = "PAPER_LIC_PANEL_SERVER_ID";
    private static final String ENV_PANEL_AUTH_HEADER_NAME = "PAPER_LIC_PANEL_AUTH_HEADER_NAME";
    private static final String ENV_PANEL_AUTH_HEADER_PREFIX = "PAPER_LIC_PANEL_AUTH_HEADER_PREFIX";
    private static final String ENV_PANEL_TIMEOUT_CONNECT_MS = "PAPER_LIC_PANEL_TIMEOUT_CONNECT_MS";
    private static final String ENV_PANEL_TIMEOUT_REQUEST_MS = "PAPER_LIC_PANEL_TIMEOUT_REQUEST_MS";
    private static final String ENV_PANEL_ENDPOINT_VALIDATE = "PAPER_LIC_PANEL_ENDPOINT_VALIDATE";
    private static final String ENV_PANEL_ENDPOINT_ISSUE = "PAPER_LIC_PANEL_ENDPOINT_ISSUE";
    private static final String ENV_PANEL_ENDPOINT_REVOKE = "PAPER_LIC_PANEL_ENDPOINT_REVOKE";
    private static final String ENV_PANEL_ENDPOINT_GET = "PAPER_LIC_PANEL_ENDPOINT_GET";

    private PluginLicenseService licenseService;

    @Override
    public void onEnable() {
        String signingSecret = readSetting(ENV_SIGNING_SECRET, "").trim();
        if (signingSecret.length() < 16) {
            getLogger().severe(ENV_SIGNING_SECRET + " must be at least 16 characters.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LicenseRepository repository = createRepository();
        LicenseMode mode = LicenseMode.fromConfig(readSetting(ENV_MODE, "HYBRID"));
        PanelLicenseClient panelClient = createPanelClient();
        HmacLicenseSigner signer = new HmacLicenseSigner(signingSecret);

        this.licenseService = new HybridLicenseService(this, repository, signer, mode, panelClient);
        getServer().getServicesManager().register(PluginLicenseService.class, licenseService, this, ServicePriority.Normal);

        getLogger().info("Licensing API enabled. mode=" + mode.name());
    }

    @Override
    public void onDisable() {
        if (licenseService != null) {
            getServer().getServicesManager().unregister(PluginLicenseService.class, licenseService);
        }
    }

    private LicenseRepository createRepository() {
        StorageType type = StorageType.fromConfig(readSetting(ENV_STORAGE_TYPE, "SQLITE"));
        return switch (type) {
            case YAML -> new YamlLicenseRepository(this);
            case MYSQL -> {
                String url = readSetting(ENV_MYSQL_JDBC_URL, "");
                String username = readSetting(ENV_MYSQL_USERNAME, "");
                String password = readSetting(ENV_MYSQL_PASSWORD, "");
                yield new JdbcLicenseRepository(this, url, username, password);
            }
            case SQLITE -> {
                File dbFile = new File(getDataFolder(), readSetting(ENV_SQLITE_FILE, "licenses.db"));
                if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                    getLogger().warning("Could not create plugin data directory for SQLite DB.");
                }
                String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                yield new JdbcLicenseRepository(this, jdbcUrl, "", "");
            }
        };
    }

    private PanelLicenseClient createPanelClient() {
        boolean enabled = Boolean.parseBoolean(readSetting(ENV_PANEL_ENABLED, "false"));
        if (!enabled) {
            return null;
        }

        String baseUrl = readSetting(ENV_PANEL_BASE_URL, "").trim();
        if (baseUrl.isBlank()) {
            getLogger().warning("Panel is enabled but " + ENV_PANEL_BASE_URL + " is empty. Falling back to local only.");
            return null;
        }

        String token = readSetting(ENV_PANEL_API_TOKEN, "").trim();
        String headerName = readSetting(ENV_PANEL_AUTH_HEADER_NAME, "Authorization");
        String headerPrefix = readSetting(ENV_PANEL_AUTH_HEADER_PREFIX, "Bearer ");
        String headerValue = headerPrefix + token;

        return new HttpPanelLicenseClient(
                this,
                baseUrl,
                headerName,
                headerValue,
                readSetting(ENV_PANEL_SERVER_ID, "default"),
                readIntSetting(ENV_PANEL_TIMEOUT_CONNECT_MS, 3000),
                readIntSetting(ENV_PANEL_TIMEOUT_REQUEST_MS, 5000),
                readSetting(ENV_PANEL_ENDPOINT_VALIDATE, "/api/licenses/validate"),
                readSetting(ENV_PANEL_ENDPOINT_ISSUE, "/api/licenses/issue"),
                readSetting(ENV_PANEL_ENDPOINT_REVOKE, "/api/licenses/revoke"),
                readSetting(ENV_PANEL_ENDPOINT_GET, "/api/licenses/get")
        );
    }

    private String readSetting(String key, String def) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return def;
    }

    private int readIntSetting(String key, int def) {
        try {
            return Integer.parseInt(readSetting(key, String.valueOf(def)));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
