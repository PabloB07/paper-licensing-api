package com.blancocl.licensing.api;

public enum LicenseValidationResult {
    VALID,
    NOT_FOUND,
    WRONG_PLUGIN,
    EXPIRED,
    REVOKED,
    SIGNATURE_INVALID,
    REMOTE_ERROR
}
