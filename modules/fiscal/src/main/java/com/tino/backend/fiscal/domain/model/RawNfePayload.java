package com.tino.backend.fiscal.domain.model;

import java.util.Objects;

/** Raw provider evidence kept out of logs and out of the canonical domain model. */
public record RawNfePayload(String json, String provider, String providerVersion) {
    public RawNfePayload {
        Objects.requireNonNull(json, "raw JSON");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerVersion, "provider version");
    }
}
