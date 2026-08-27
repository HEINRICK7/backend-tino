package com.tino.backend.device.application.model;

import com.tino.backend.device.domain.model.DeviceInstallationId;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;

/** Framework-independent context for an active installation in an authorized Business. */
public record ActiveDeviceInstallationContext(
        DeviceInstallationId installationId,
        InstallationExternalId installationExternalId,
        BusinessId businessId) {
    public ActiveDeviceInstallationContext {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(installationExternalId, "installationExternalId");
        Objects.requireNonNull(businessId, "businessId");
    }
}
