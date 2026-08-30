package com.tino.backend.receiving.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Stable mobile-facing confirmation decisions. */
@Schema(enumAsRef = true)
public enum DecisionAction {
    USE_EXISTING,
    CREATE_PRODUCT,
    IGNORE
}
