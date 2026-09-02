package com.tino.backend.fiscal.application.model;

import com.tino.backend.fiscal.domain.model.CanonicalNfeDocument;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;

public record NfeRetrievalResult(
        RetrievalStatus retrievalStatus,
        RawNfePayload rawPayload,
        CanonicalNfeDocument document,
        String failureCode) {
    public static NfeRetrievalResult success(RawNfePayload raw, CanonicalNfeDocument document) {
        return new NfeRetrievalResult(RetrievalStatus.SUCCESS, raw, document, null);
    }

    public static NfeRetrievalResult failure(RetrievalStatus status, String code, RawNfePayload raw) {
        return new NfeRetrievalResult(status, raw, null, code);
    }
}
