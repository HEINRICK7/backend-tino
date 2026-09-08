package com.tino.backend.messaging.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record DebtEntryView(
        LocalDate occurredAt,
        DebtEntryDisplayType type,
        String description,
        MoneyView amount) {
    public DebtEntryView {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(type, "type");
        if (description == null || description.isBlank() || description.length() > 160) {
            throw new IllegalArgumentException("invalid debt entry description");
        }
        Objects.requireNonNull(amount, "amount");
    }
}
