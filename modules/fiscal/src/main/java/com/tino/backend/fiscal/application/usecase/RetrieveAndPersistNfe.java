package com.tino.backend.fiscal.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAccess;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.exception.NfeIdempotencyConflictException;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.application.port.out.NfeRetrievalPort;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Authorizes first, calls SERPRO outside a DB transaction, then persists in a short tenant transaction. */
public final class RetrieveAndPersistNfe {
    private final BusinessAccess access;
    private final TenantContextExecutor tenants;
    private final NfeRetrievalPort provider;
    private final NfeDocumentRepository documents;
    private final UuidGenerator ids;
    private final Clock clock;

    public RetrieveAndPersistNfe(BusinessAccess access, TenantContextExecutor tenants,
            NfeRetrievalPort provider, NfeDocumentRepository documents, UuidGenerator ids, Clock clock) {
        this.access = Objects.requireNonNull(access); this.tenants = Objects.requireNonNull(tenants);
        this.provider = Objects.requireNonNull(provider); this.documents = Objects.requireNonNull(documents);
        this.ids = Objects.requireNonNull(ids); this.clock = Objects.requireNonNull(clock);
    }

    public NfeDocumentSnapshot execute(UUID userId, BusinessId requestedBusiness, NfeAccessKey key) {
        return execute(userId, requestedBusiness, key, "nfe-" + key.value());
    }

    public NfeDocumentSnapshot execute(UUID userId, BusinessId requestedBusiness, NfeAccessKey key, String idempotencyKey) {
        var authorizedBusiness = access.require(userId, requestedBusiness);
        var existing = tenants.execute(authorizedBusiness, () -> documents.findIdempotency(authorizedBusiness, idempotencyKey)
                .map(value -> documents.find(authorizedBusiness, value.documentId()).orElse(null)));
        if (existing.isPresent() && !existing.get().accessKey().equals(key)) throw new NfeIdempotencyConflictException();
        existing = existing.isPresent() ? existing : tenants.execute(authorizedBusiness, () -> documents.findByAccessKey(authorizedBusiness, key));
        if (existing.isPresent() && existing.get().retrievalStatus() == RetrievalStatus.SUCCESS) return existing.get();
        var documentId = existing.map(NfeDocumentSnapshot::id).orElseGet(ids::next);
        if (existing.isEmpty()) {
            var claimDocumentId = documentId;
            var claimed = tenants.execute(authorizedBusiness, () -> documents.claimIdempotency(authorizedBusiness, idempotencyKey, key.value(), claimDocumentId, Instant.now(clock)));
            if (!claimed) {
                var claimedDocument = tenants.execute(authorizedBusiness, () -> documents.findIdempotency(authorizedBusiness, idempotencyKey).orElseThrow().documentId());
                documentId = claimedDocument;
            }
        }
        var result = provider.retrieve(key);
        var persistedDocumentId = documentId;
        return tenants.execute(authorizedBusiness, () -> documents.save(authorizedBusiness, persistedDocumentId, key, result, Instant.now(clock)));
    }
}
