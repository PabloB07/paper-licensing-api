# Paper Licensing API

Paper plugin for private plugin licensing with:
- Panel/API integration (HTTP)
- HMAC-signed keys
- Storage backends: YAML, SQLite, MySQL

## Build
```bash
mvn clean package
```

Jar:
- `target/paper-licensing-api-1.0.0.jar`

## Runtime Modes
Set `PAPER_LIC_MODE` (env var or JVM `-D` property):
- `LOCAL`: only local storage validation.
- `REMOTE`: panel API validation.
- `HYBRID`: panel first, local fallback on API errors.

## HMAC-signed keys
Keys are generated and validated with `PAPER_LIC_SIGNING_SECRET`.
Use the same secret in your TypeScript panel so both sides verify the same key format.

Key format:
- `<nonce>.<signature>`
- signature: HMAC-SHA256 truncated and base64url encoded.

## Storage
Set `PAPER_LIC_STORAGE_TYPE`:
- `YAML`
- `SQLITE` (default, file in plugin data folder)
- `MYSQL` (set `PAPER_LIC_MYSQL_JDBC_URL`, `PAPER_LIC_MYSQL_USERNAME`, `PAPER_LIC_MYSQL_PASSWORD`)

`SQLITE` file name: `PAPER_LIC_SQLITE_FILE` (default `licenses.db`)

## Panel endpoint contract
All calls are POST JSON.

Panel settings (env or `-D`):
- `PAPER_LIC_PANEL_ENABLED=true|false`
- `PAPER_LIC_PANEL_BASE_URL`
- `PAPER_LIC_PANEL_API_TOKEN`
- `PAPER_LIC_PANEL_SERVER_ID`
- `PAPER_LIC_PANEL_AUTH_HEADER_NAME` (default `Authorization`)
- `PAPER_LIC_PANEL_AUTH_HEADER_PREFIX` (default `Bearer `)
- `PAPER_LIC_PANEL_TIMEOUT_CONNECT_MS` (default `3000`)
- `PAPER_LIC_PANEL_TIMEOUT_REQUEST_MS` (default `5000`)
- `PAPER_LIC_PANEL_ENDPOINT_VALIDATE` (default `/api/licenses/validate`)
- `PAPER_LIC_PANEL_ENDPOINT_ISSUE` (default `/api/licenses/issue`)
- `PAPER_LIC_PANEL_ENDPOINT_REVOKE` (default `/api/licenses/revoke`)
- `PAPER_LIC_PANEL_ENDPOINT_GET` (default `/api/licenses/get`)

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

## Library-only plugin
This plugin exposes only a Bukkit service (`PluginLicenseService`) and does not register any commands.

## Using the service from private plugins
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

## TypeScript panel example (verify key signature)
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
