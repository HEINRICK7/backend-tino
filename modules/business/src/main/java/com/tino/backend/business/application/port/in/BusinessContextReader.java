package com.tino.backend.business.application.port.in;

import java.util.List;
import java.util.UUID;

/** Public, read-only Business view used by Bootstrap composition. */
public interface BusinessContextReader {
    List<AccessibleBusinessView> listAccessibleBusinesses(UUID userId);
}
