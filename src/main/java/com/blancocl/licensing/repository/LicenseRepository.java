package com.blancocl.licensing.repository;

import com.blancocl.licensing.model.LicenseRecord;

import java.util.Optional;

public interface LicenseRepository {
    void upsert(LicenseRecord record);

    Optional<LicenseRecord> find(String key);

    boolean revoke(String key);
}
