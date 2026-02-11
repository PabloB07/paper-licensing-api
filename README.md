# Paper Licensing API

Shared licensing plugin for Paper servers. It exposes a Bukkit service (`PluginLicenseService`) that other private plugins can use to issue, validate, revoke, and fetch licenses.

## Features
- HMAC-signed license keys (`nonce.signature`)
- Runtime modes: `LOCAL`, `REMOTE`, `HYBRID`
- Storage backends: `SQLITE` (default), `MYSQL`, `YAML`
- Optional HTTP panel integration for centralized license management
- Service-only plugin (no commands)

## Requirements
- Java 21+
- Paper 1.21.x

## Build
```bash
mvn clean package
```

Output JAR:
- `target/paper-licensing-api-1.0.0.jar`

## Install
1. Build the project.
2. Put the JAR in your server `plugins/` folder.
3. Configure environment variables or JVM properties.
4. Start/restart the server.

All settings can be provided as either:
- Environment variables (recommended)
- JVM system properties (`-DKEY=value`)

## Required Configuration
`PAPER_LIC_SIGNING_SECRET`
- Required on startup.
- Must be at least 16 characters.
- Must match the secret used by your external panel if keys are validated there.

If this value is missing/too short, the plugin disables itself.

## Core Configuration
`PAPER_LIC_MODE`
- Values: `LOCAL`, `REMOTE`, `HYBRID`
- Default: `HYBRID`

Mode behavior:
- `LOCAL`: validates only against local storage.
- `REMOTE`: validates against panel only; panel errors return `REMOTE_ERROR`.
- `HYBRID`: panel first; falls back to local when panel validation fails.

`PAPER_LIC_STORAGE_TYPE`
- Values: `SQLITE`, `MYSQL`, `YAML`
- Default: `SQLITE`

### SQLite
`PAPER_LIC_SQLITE_FILE`
- Default: `licenses.db`
- Stored under the plugin data folder.

### MySQL
`PAPER_LIC_MYSQL_JDBC_URL`
- Example: `jdbc:mysql://127.0.0.1:3306/licensing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`

`PAPER_LIC_MYSQL_USERNAME`

`PAPER_LIC_MYSQL_PASSWORD`

### YAML
No extra settings. Data is written to:
- `plugins/PaperLicensingAPI/licenses.yml`

## Panel/API Configuration
Set `PAPER_LIC_PANEL_ENABLED=true` to enable remote calls.

`PAPER_LIC_PANEL_BASE_URL`
- Required when panel is enabled.
- Example: `https://panel.example.com`

`PAPER_LIC_PANEL_API_TOKEN`

`PAPER_LIC_PANEL_SERVER_ID`
- Default: `default`

`PAPER_LIC_PANEL_AUTH_HEADER_NAME`
- Default: `Authorization`

`PAPER_LIC_PANEL_AUTH_HEADER_PREFIX`
- Default: `Bearer `

`PAPER_LIC_PANEL_TIMEOUT_CONNECT_MS`
- Default: `3000`

`PAPER_LIC_PANEL_TIMEOUT_REQUEST_MS`
- Default: `5000`

Endpoints (relative paths accepted):
- `PAPER_LIC_PANEL_ENDPOINT_VALIDATE` (default `/api/licenses/validate`)
- `PAPER_LIC_PANEL_ENDPOINT_ISSUE` (default `/api/licenses/issue`)
- `PAPER_LIC_PANEL_ENDPOINT_REVOKE` (default `/api/licenses/revoke`)
- `PAPER_LIC_PANEL_ENDPOINT_GET` (default `/api/licenses/get`)

## Panel Contract
All endpoints are `POST` with JSON.

### Validate
Request:
```json
{"pluginId":"myplugin","key":"...","serverId":"survival-01"}
```

Response:
```json
{"result":"VALID","license":{"key":"...","pluginId":"myplugin","owner":"owner","issuedAt":1700000000,"expiresAt":-1,"revoked":false}}
```

### Issue
Request:
```json
{"pluginId":"myplugin","owner":"owner","validDays":30,"serverId":"survival-01"}
```

Response:
```json
{"license":{"key":"...","pluginId":"myplugin","owner":"owner","issuedAt":1700000000,"expiresAt":-1,"revoked":false}}
```

### Revoke
Request:
```json
{"key":"...","serverId":"survival-01"}
```

Response:
```json
{"success":true}
```

### Get
Request:
```json
{"key":"...","serverId":"survival-01"}
```

Response:
```json
{"license":{"key":"...","pluginId":"myplugin","owner":"owner","issuedAt":1700000000,"expiresAt":-1,"revoked":false}}
```

## Service API for Other Plugins
Service interface:
- `issueLicense(String pluginId, String owner, int validDays)`
- `validate(String pluginId, String key)`
- `revoke(String key)`
- `get(String key)`

Validation results:
- `VALID`
- `NOT_FOUND`
- `WRONG_PLUGIN`
- `EXPIRED`
- `REVOKED`
- `SIGNATURE_INVALID`
- `REMOTE_ERROR`

Example usage:
```java
RegisteredServiceProvider<PluginLicenseService> provider =
        Bukkit.getServicesManager().getRegistration(PluginLicenseService.class);
if (provider == null) {
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}

PluginLicenseService licensing = provider.getProvider();
LicenseValidationResult result = licensing.validate("myplugin", "LICENSE_KEY");
if (result != LicenseValidationResult.VALID) {
    getLogger().severe("License invalid: " + result);
    Bukkit.getPluginManager().disablePlugin(this);
}
```

## TypeScript Key Verification Example
```ts
import crypto from "node:crypto";

function verifyKey(pluginId: string, key: string, secret: string): boolean {
  const idx = key.lastIndexOf(".");
  if (idx <= 0) return false;

  const nonce = key.slice(0, idx);
  const sig = key.slice(idx + 1);
  const payload = `${pluginId.toLowerCase()}:${nonce}`;
  const expected = crypto
    .createHmac("sha256", secret)
    .update(payload, "utf8")
    .digest()
    .subarray(0, 16)
    .toString("base64url");

  return crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected));
}
```
