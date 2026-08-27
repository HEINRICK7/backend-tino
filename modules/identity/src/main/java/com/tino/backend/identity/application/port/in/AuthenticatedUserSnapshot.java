package com.tino.backend.identity.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Minimal public identity result for other modules; no User aggregate crosses the boundary. */
public record AuthenticatedUserSnapshot(UUID userId, boolean active) {
    public AuthenticatedUserSnapshot {
        Objects.requireNonNull(userId, "userId");
    }
}
