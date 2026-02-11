package com.blancocl.licensing.service;

public enum LicenseMode {
    LOCAL,
    REMOTE,
    HYBRID;

    public static LicenseMode fromConfig(String raw) {
        if (raw == null) {
            return HYBRID;
        }
        try {
            return LicenseMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return HYBRID;
        }
    }
}
