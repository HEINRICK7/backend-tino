package com.tino.backend.businessunderstanding.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable decision produced by the context-only purpose resolver. */
public record ItemPurposeResolutionDecision(
        ItemPurpose purpose,
        BigDecimal confidence,
        String resolution,
        ItemPurposeAuthority authority,
        boolean needsConfirmation,
        List<ItemPurpose> suggestions,
        List<ItemPurposeResolutionEvidence> evidence) {
    public ItemPurposeResolutionDecision {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between zero and one");
        }
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("resolution is required");
        }
        Objects.requireNonNull(authority, "authority");
        suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
