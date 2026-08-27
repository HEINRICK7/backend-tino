package com.tino.backend.shared.kernel;

import java.util.UUID;

/**
 * Generates identifiers for domain entities.
 */
@FunctionalInterface
public interface UuidGenerator {
    UUID next();
}
