package com.tino.backend.catalog.application.model;

import java.util.UUID;

public record ProductResolution(Status status, UUID productId, String name, String baseUnit) {
    public enum Status { MATCHED, NEW_CANDIDATE, NEEDS_REVIEW }
}
