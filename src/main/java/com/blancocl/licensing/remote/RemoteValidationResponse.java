package com.blancocl.licensing.remote;

import com.blancocl.licensing.api.LicenseValidationResult;
import com.blancocl.licensing.model.LicenseRecord;

public record RemoteValidationResponse(LicenseValidationResult result, LicenseRecord record) {
}
