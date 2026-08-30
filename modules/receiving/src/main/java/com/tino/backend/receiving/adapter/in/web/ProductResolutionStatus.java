package com.tino.backend.receiving.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Stable mobile-facing product resolution states. */
@Schema(enumAsRef = true)
public enum ProductResolutionStatus {
    MATCHED,
    NEW_CANDIDATE,
    NEEDS_REVIEW,
    IGNORED
}
