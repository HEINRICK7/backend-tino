package com.tino.backend.device.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Public read-only Device contract; Business authorization remains inside M4. */
public interface DeviceInstallationContextReader {
    Optional<ActiveInstallationView> resolve(
            UUID authenticatedUserId, UUID requestedBusinessId, String installationExternalId);
}
