package com.tino.backend.receiving.application.model;

import java.util.UUID;
import java.util.List;

/** Read model returned by the versioned PurchaseDocument preview boundary. */
public record PurchasePreviewSnapshot(
        UUID previewId,
        UUID documentId,
        String status,
        long version,
        PurchaseDocument document,
        List<PurchaseDocumentMatch> matches) {
    public PurchasePreviewSnapshot {
        if (previewId == null) throw new IllegalArgumentException("preview id is required");
        if (documentId == null) throw new IllegalArgumentException("document id is required");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("preview status is required");
        if (version < 0) throw new IllegalArgumentException("preview version must not be negative");
        if (document == null) throw new IllegalArgumentException("purchase document is required");
        if (matches == null) throw new IllegalArgumentException("purchase document matches are required");
        matches = List.copyOf(matches);
    }

    public int totalItems() { return document.items().size(); }

    public long count(PurchaseDocumentMatch.Status status) {
        return matches.stream().filter(match -> match.status() == status).count();
    }
}
