package com.tino.backend.credit.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.exception.CreditCompensationException;
import com.tino.backend.credit.application.exception.CreditConflictException;
import com.tino.backend.credit.application.exception.CreditCustomerNotFoundException;
import com.tino.backend.credit.application.exception.CreditEntryNotFoundException;
import com.tino.backend.credit.application.exception.CreditInsufficientBalanceException;
import com.tino.backend.credit.application.model.CreditOperationResult;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CompensateCreditEntry {
    private static final String OPERATION = "COMPENSATE_ENTRY";
    private final BusinessAuthorization authorization;
    private final CreditRepository credits;
    private final UuidGenerator ids;
    private final Clock clock;

    public CompensateCreditEntry(BusinessAuthorization authorization, CreditRepository credits,
            UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.credits = Objects.requireNonNull(credits, "credits");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreditOperationResult execute(UUID userId, BusinessId businessId, UUID customerId,
            UUID originalEntryId, String idempotencyKey, String fingerprint) {
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
            var compensationId = ids.next();
            if (!credits.claimIdempotency(authorizedBusiness, OPERATION, idempotencyKey,
                    fingerprint, compensationId, now)) {
                var concurrent = credits.findIdempotency(authorizedBusiness, OPERATION, idempotencyKey)
                        .orElseThrow(CreditConflictException::new);
                return replay(authorizedBusiness, customerId, concurrent, fingerprint);
            }

            var original = credits.findEntry(authorizedBusiness, customerId, originalEntryId, false)
                    .orElseThrow(CreditEntryNotFoundException::new);
            if (original.compensatesEntryId() != null) {
                throw new CreditCompensationException("only original entries can be compensated");
            }
            if (credits.findCompensation(authorizedBusiness, original.id()).isPresent()) {
                throw new CreditCompensationException("credit entry is already compensated");
            }
            var account = credits.findAccountById(authorizedBusiness, original.accountId(), true)
                    .orElseThrow(CreditEntryNotFoundException::new);
            var direction = original.direction().opposite();
            if (direction == CreditDirection.DEBIT
                    && account.balance().compareTo(original.amount().value()) < 0) {
                throw new CreditInsufficientBalanceException();
            }
            var compensation = new CreditLedgerEntry(compensationId, authorizedBusiness, account.id(),
                    customerId, direction, original.amount(), "COMPENSATION", original.id(), userId, now);
            credits.insertEntry(compensation);
            credits.insertAudit(new CreditRepository.AuditRecord(ids.next(), authorizedBusiness, OPERATION,
                    compensation.id(), idempotencyKey, userId, fingerprint, now));
            var updated = credits.findAccountById(authorizedBusiness, account.id(), false).orElseThrow();
            return new CreditOperationResult(compensation, updated, false);
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
