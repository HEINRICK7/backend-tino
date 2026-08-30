package com.tino.backend.fiscal.application.port.out;

import com.tino.backend.fiscal.domain.model.CanonicalNfeDocument;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;

/** Versioned parser contract used both by the provider adapter and local reprocessing. */
public interface NfeParser {
    CanonicalNfeDocument parse(String rawJson, NfeAccessKey requestedKey);
}
