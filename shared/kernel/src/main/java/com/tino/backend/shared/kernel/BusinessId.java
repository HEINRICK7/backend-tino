package com.tino.backend.shared.kernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identifier for the business that owns a tenant-scoped operation.
 *
 * <p>The value is deliberately a small kernel type rather than a database or
 * web type. Resolving a business from an authenticated user and membership is
 * owned by a later milestone.</p>
 */
public record BusinessId(UUID value) {
    public BusinessId {
        Objects.requireNonNull(value, "value");
    }
}
