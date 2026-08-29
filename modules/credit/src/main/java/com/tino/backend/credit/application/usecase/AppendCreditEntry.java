package com.tino.backend.credit.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.exception.CreditConflictException;
import com.tino.backend.credit.application.exception.CreditCustomerNotFoundException;
import com.tino.backend.credit.application.exception.CreditInsufficientBalanceException;
import com.tino.backend.credit.application.model.CreditOperationResult;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.credit.domain.model.CreditAmount;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AppendCreditEntry {
    private static final String OPERATION = "APPEND_ENTRY";
    private final BusinessAuthorization authorization;
    private final CreditRepository credits;
    private final UuidGenerator ids;
    private final Clock clock;

    public AppendCreditEntry(BusinessAuthorization authorization, CreditRepository credits,
            UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.credits = Objects.requireNonNull(credits, "credits");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreditOperationResult execute(UUID userId, BusinessId businessId, UUID customerId,
            CreditDirection direction, BigDecimal amount, String reason, String idempotencyKey,
            String fingerprint) {
        Objects.requireNonNull(direction, "direction");
        var creditAmount = CreditAmount.of(amount);
        validateKey(idempotencyKey);
        validateFingerprint(fingerprint);
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            if (!credits.customerExists(authorizedBusiness, customerId)) {
                throw new CreditCustomerNotFoundException();
            }
            var existing = credits.findIdempotency(authorizedBusiness, OPERATION, idempotencyKey);
            if (existing.isPresent()) {
                return replay(authorizedBusiness, customerId, existing.orElseThrow(), fingerprint);
            }

            var now = Instant.now(clock);
            var entryId = ids.next();
            if (!credits.claimIdempotency(authorizedBusiness, OPERATION, idempotencyKey,
                    fingerprint, entryId, now)) {
                var concurrent = credits.findIdempotency(authorizedBusiness, OPERATION, idempotencyKey)
                        .orElseThrow(CreditConflictException::new);
                return replay(authorizedBusiness, customerId, concurrent, fingerprint);
            }

            var account = credits.findOrCreateAccountForUpdate(
                    authorizedBusiness, customerId, ids.next(), now);
            if (direction == CreditDirection.DEBIT
                    && account.balance().compareTo(creditAmount.value()) < 0) {
                throw new CreditInsufficientBalanceException();
            }
            var entry = new CreditLedgerEntry(entryId, authorizedBusiness, account.id(), customerId,
                    direction, creditAmount, reason, null, userId, now);
            credits.insertEntry(entry);
            credits.insertAudit(new CreditRepository.AuditRecord(ids.next(), authorizedBusiness, OPERATION,
                    entry.id(), idempotencyKey, userId, fingerprint, now));
            var updated = credits.findAccountById(authorizedBusiness, account.id(), false).orElseThrow();
            return new CreditOperationResult(entry, updated, false);
        });
    }

    private CreditOperationResult replay(BusinessId businessId, UUID customerId,
            CreditRepository.IdempotencyRecord record, String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) {
            throw new CreditConflictException();
        }
        var entry = credits.findEntry(businessId, customerId, record.entryId(), false)
                .orElseThrow(CreditConflictException::new);
        var account = credits.findAccountById(businessId, entry.accountId(), false)
                .orElseThrow(CreditConflictException::new);
        return new CreditOperationResult(entry, account, true);
    }

    private static void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private static void validateFingerprint(String value) {
        if (value == null || value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid request fingerprint");
        }
    }
}
