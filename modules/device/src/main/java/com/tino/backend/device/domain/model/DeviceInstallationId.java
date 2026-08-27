package com.tino.backend.device.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Opaque server identifier for one logical application installation. */
public record DeviceInstallationId(UUID value) {
    public DeviceInstallationId {
        Objects.requireNonNull(value, "value");
    }
}
