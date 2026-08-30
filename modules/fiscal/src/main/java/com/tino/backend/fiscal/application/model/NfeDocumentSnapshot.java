package com.tino.backend.fiscal.application.model;

import com.tino.backend.fiscal.domain.model.CanonicalNfeDocument;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped persisted view of a fiscal retrieval, without exposing raw data to UI. */
public record NfeDocumentSnapshot(UUID id, NfeAccessKey accessKey, RetrievalStatus retrievalStatus,
        FiscalStatus fiscalStatus, CanonicalNfeDocument document, RawNfePayload rawPayload,
        String failureCode, long version, Instant updatedAt) {}
