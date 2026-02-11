package com.blancocl.licensing.service;

public enum StorageType {
    YAML,
    SQLITE,
    MYSQL;

    public static StorageType fromConfig(String raw) {
        if (raw == null) {
            return SQLITE;
        }
        try {
            return StorageType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SQLITE;
        }
    }
}
