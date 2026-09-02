package com.tino.backend.credit.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.UUID;

public record CreditLedgerEntry(
        UUID id,
        BusinessId businessId,
        UUID accountId,
        UUID customerId,
        CreditDirection direction,
        CreditAmount amount,
        String reason,
        UUID compensatesEntryId,
        UUID actorUserId,
        Instant createdAt) {
    public CreditLedgerEntry {
        if (reason == null || reason.isBlank() || reason.length() > 64) {
            throw new IllegalArgumentException("credit reason must be nonblank and at most 64 characters");
        }
        if (compensatesEntryId != null && compensatesEntryId.equals(id)) {
            throw new IllegalArgumentException("credit entry cannot compensate itself");
        }
    }
}
