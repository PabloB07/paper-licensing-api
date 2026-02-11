package com.blancocl.licensing.api;

import com.blancocl.licensing.model.LicenseRecord;

import java.util.Optional;

public interface PluginLicenseService {
    LicenseRecord issueLicense(String pluginId, String owner, int validDays);

    LicenseValidationResult validate(String pluginId, String key);

    boolean revoke(String key);

    Optional<LicenseRecord> get(String key);
}
