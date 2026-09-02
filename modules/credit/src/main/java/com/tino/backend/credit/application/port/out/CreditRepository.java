package com.tino.backend.credit.application.port.out;

import com.tino.backend.credit.domain.model.CreditAccount;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CreditRepository {
    boolean customerExists(BusinessId businessId, UUID customerId);

    Optional<CreditAccount> findAccount(BusinessId businessId, UUID customerId);

    Optional<CreditAccount> findAccountById(BusinessId businessId, UUID accountId, boolean lock);

    CreditAccount findOrCreateAccountForUpdate(
            BusinessId businessId, UUID customerId, UUID proposedAccountId, Instant now);

    Optional<CreditLedgerEntry> findEntry(
            BusinessId businessId, UUID customerId, UUID entryId, boolean lock);

    Optional<CreditLedgerEntry> findCompensation(BusinessId businessId, UUID originalEntryId);

    Optional<IdempotencyRecord> findIdempotency(
            BusinessId businessId, String operation, String key);

    boolean claimIdempotency(
            BusinessId businessId, String operation, String key, String fingerprint,
            UUID entryId, Instant createdAt);

    void insertEntry(CreditLedgerEntry entry);

    void insertAudit(AuditRecord audit);

    record IdempotencyRecord(String fingerprint, UUID entryId) {}

    record AuditRecord(
            UUID id,
            BusinessId businessId,
            String operation,
            UUID entryId,
            String idempotencyKey,
            UUID actorUserId,
            String requestFingerprint,
            Instant createdAt) {}
}
