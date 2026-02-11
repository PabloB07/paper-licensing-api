package com.blancocl.licensing.remote;

import com.blancocl.licensing.model.LicenseRecord;

import java.util.Optional;

public interface PanelLicenseClient {
    RemoteValidationResponse validate(String pluginId, String key);

    Optional<LicenseRecord> issue(String pluginId, String owner, int validDays);

    boolean revoke(String key);

    Optional<LicenseRecord> get(String key);
}
