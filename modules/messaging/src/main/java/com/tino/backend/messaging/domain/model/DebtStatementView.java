package com.tino.backend.messaging.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DebtStatementView(
        String businessName,
        String customerDisplayName,
        MoneyView openBalance,
        DebtDisplayStatus status,
        List<DebtEntryView> entries,
        Instant generatedAt) {
    public DebtStatementView {
        if (businessName == null || businessName.isBlank() || businessName.length() > 200) {
            throw new IllegalArgumentException("invalid business name");
        }
        if (customerDisplayName == null || customerDisplayName.isBlank() || customerDisplayName.length() > 200) {
            throw new IllegalArgumentException("invalid customer name");
        }
        Objects.requireNonNull(openBalance, "openBalance");
        Objects.requireNonNull(status, "status");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(generatedAt, "generatedAt");
    }
}
