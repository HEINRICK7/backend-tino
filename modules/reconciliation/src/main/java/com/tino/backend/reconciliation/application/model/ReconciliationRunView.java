package com.tino.backend.reconciliation.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationRunView(UUID id, UUID businessId, String provider, String state,
        int totalCount, int matchedCount, int discrepancyCount, Instant createdAt,
        Instant completedAt, List<ReconciliationItemView> items) {
    public ReconciliationRunView {
        items = List.copyOf(items);
    }
}
