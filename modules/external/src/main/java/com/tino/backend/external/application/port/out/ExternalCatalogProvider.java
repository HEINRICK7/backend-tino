package com.tino.backend.external.application.port.out;

import com.tino.backend.external.application.model.ExternalCatalogPage;
import java.time.Instant;
import java.util.UUID;

/** Generic read-only provider boundary. Application code knows no provider DTO or HTTP detail. */
public interface ExternalCatalogProvider {
    String provider();
    ExternalCatalogPage fetch(UUID connectionId, String cursor, Instant watermark);
}
