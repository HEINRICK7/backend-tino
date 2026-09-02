package com.tino.backend.businessunderstanding.domain.model;

import java.util.Objects;

/** Evidence exposed by the resolver so a suggestion can be explained. */
public record ItemPurposeResolutionEvidence(
        String signal, ItemPurpose candidate, String detail) {
    public ItemPurposeResolutionEvidence {
        if (signal == null || signal.isBlank()) {
            throw new IllegalArgumentException("evidence signal is required");
        }
        Objects.requireNonNull(candidate, "candidate");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("evidence detail is required");
        }
        signal = signal.trim();
        detail = detail.trim();
    }
}
