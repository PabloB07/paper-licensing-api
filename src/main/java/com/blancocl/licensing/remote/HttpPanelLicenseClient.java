package com.blancocl.licensing.remote;

import com.blancocl.licensing.api.LicenseValidationResult;
import com.blancocl.licensing.model.LicenseRecord;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class HttpPanelLicenseClient implements PanelLicenseClient {
    private final JavaPlugin plugin;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeaderName;
    private final String authHeaderValue;
    private final String serverId;
    private final Duration requestTimeout;
    private final String validateEndpoint;
    private final String issueEndpoint;
    private final String revokeEndpoint;
    private final String getEndpoint;

    public HttpPanelLicenseClient(
            JavaPlugin plugin,
            String baseUrl,
            String authHeaderName,
            String authHeaderValue,
            String serverId,
            int connectTimeoutMs,
            int requestTimeoutMs,
            String validateEndpoint,
            String issueEndpoint,
            String revokeEndpoint,
            String getEndpoint
    ) {
        this.plugin = plugin;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.authHeaderName = authHeaderName;
        this.authHeaderValue = authHeaderValue;
        this.serverId = serverId;
        this.requestTimeout = Duration.ofMillis(Math.max(1000, requestTimeoutMs));
        this.validateEndpoint = validateEndpoint;
        this.issueEndpoint = issueEndpoint;
        this.revokeEndpoint = revokeEndpoint;
        this.getEndpoint = getEndpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .build();
    }

    @Override
    public RemoteValidationResponse validate(String pluginId, String key) {
        if (isBlank(validateEndpoint)) {
            return new RemoteValidationResponse(LicenseValidationResult.REMOTE_ERROR, null);
        }

        JsonObject body = new JsonObject();
        body.addProperty("pluginId", pluginId);
        body.addProperty("key", key);
        body.addProperty("serverId", serverId);

        Optional<JsonObject> payload = post(validateEndpoint, body);
        if (payload.isEmpty()) {
            return new RemoteValidationResponse(LicenseValidationResult.REMOTE_ERROR, null);
        }

        JsonObject json = payload.get();
        LicenseValidationResult result = parseResult(json.get("result"));
        LicenseRecord record = parseRecord(json.getAsJsonObject("license"));
        return new RemoteValidationResponse(result, record);
    }

    @Override
    public Optional<LicenseRecord> issue(String pluginId, String owner, int validDays) {
        if (isBlank(issueEndpoint)) {
            return Optional.empty();
        }

        JsonObject body = new JsonObject();
        body.addProperty("pluginId", pluginId);
        body.addProperty("owner", owner);
        body.addProperty("validDays", validDays);
        body.addProperty("serverId", serverId);

        Optional<JsonObject> payload = post(issueEndpoint, body);
        if (payload.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(parseRecord(payload.get().getAsJsonObject("license")));
    }

    @Override
    public boolean revoke(String key) {
        if (isBlank(revokeEndpoint)) {
            return false;
        }

        JsonObject body = new JsonObject();
        body.addProperty("key", key);
        body.addProperty("serverId", serverId);

        Optional<JsonObject> payload = post(revokeEndpoint, body);
        return payload.map(json -> json.has("success") && json.get("success").getAsBoolean()).orElse(false);
    }

    @Override
    public Optional<LicenseRecord> get(String key) {
        if (isBlank(getEndpoint)) {
            return Optional.empty();
        }

        JsonObject body = new JsonObject();
        body.addProperty("key", key);
        body.addProperty("serverId", serverId);

        Optional<JsonObject> payload = post(getEndpoint, body);
        if (payload.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(parseRecord(payload.get().getAsJsonObject("license")));
    }

    private Optional<JsonObject> post(String endpoint, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizeEndpoint(endpoint)))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header(authHeaderName, authHeaderValue)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().warning("Panel API " + endpoint + " returned " + response.statusCode());
                return Optional.empty();
            }

            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            return Optional.of(parsed.getAsJsonObject());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            plugin.getLogger().warning("Panel API call failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private LicenseValidationResult parseResult(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return LicenseValidationResult.REMOTE_ERROR;
        }
        String raw = element.getAsString();
        try {
            return LicenseValidationResult.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LicenseValidationResult.REMOTE_ERROR;
        }
    }

    private LicenseRecord parseRecord(JsonObject object) {
        if (object == null) {
            return null;
        }

        String key = getString(object, "key", "");
        String pluginId = getString(object, "pluginId", "");
        String owner = getString(object, "owner", "");
        Instant issuedAt = Instant.ofEpochSecond(getLong(object, "issuedAt", Instant.now().getEpochSecond()));
        long expiresAtEpoch = getLong(object, "expiresAt", -1L);
        Instant expiresAt = expiresAtEpoch < 0 ? null : Instant.ofEpochSecond(expiresAtEpoch);
        boolean revoked = getBoolean(object, "revoked", false);

        if (key.isBlank() || pluginId.isBlank()) {
            return null;
        }

        return new LicenseRecord(key, pluginId, owner, issuedAt, expiresAt, revoked);
    }

    private String getString(JsonObject object, String field, String def) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? def : value.getAsString();
    }

    private long getLong(JsonObject object, String field, long def) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? def : value.getAsLong();
    }

    private boolean getBoolean(JsonObject object, String field, boolean def) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? def : value.getAsBoolean();
    }

    private String trimTrailingSlash(String input) {
        if (input == null) {
            return "";
        }
        if (input.endsWith("/")) {
            return input.substring(0, input.length() - 1);
        }
        return input;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint.startsWith("/")) {
            return endpoint;
        }
        return "/" + endpoint;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
