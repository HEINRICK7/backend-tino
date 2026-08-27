package com.tino.backend.device.application.port.out;

import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.InstallationExternalId;
import java.util.Optional;

/** Specific persistence operations for installation registration and resolution. */
public interface DeviceInstallationRepository {
    /** Inserts only when the globally unique external id is absent; returns affected rows. */
    int insertIfAbsent(DeviceInstallation installation);

    /** Finds an installation visible in the current authorized tenant transaction. */
    Optional<DeviceInstallation> findByExternalId(InstallationExternalId externalId);
}
