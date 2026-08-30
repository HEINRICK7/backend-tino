package com.tino.backend.fiscal.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Rebuilds the canonical document from persisted raw data without calling SERPRO. */
public final class ReprocessNfe {
    private final BusinessAuthorization authorization;
    private final NfeDocumentRepository documents;
    private final NfeParser parser;
    private final Clock clock;

    public ReprocessNfe(BusinessAuthorization authorization, NfeDocumentRepository documents,
            NfeParser parser, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.documents = Objects.requireNonNull(documents);
        this.parser = Objects.requireNonNull(parser);
        this.clock = Objects.requireNonNull(clock);
    }

    public NfeDocumentSnapshot execute(UUID userId, BusinessId businessId, UUID documentId, String idempotencyKey) {
        return authorization.execute(userId, businessId, authorized -> {
            var current = documents.find(authorized, documentId)
                    .orElseThrow(() -> new IllegalArgumentException("fiscal document not found"));
            var existingKey = documents.findIdempotency(authorized, idempotencyKey);
            if (existingKey.isPresent()) {
                if (!existingKey.get().documentId().equals(documentId)) {
                    throw new IllegalArgumentException("Idempotency-Key was already used for another NF-e");
                }
                return current;
            }
            var raw = current.rawPayload();
            if (raw == null) throw new IllegalArgumentException("fiscal document has no raw payload to reprocess");
            var canonical = parser.parse(raw.json(), current.accessKey());
            var claimed = documents.claimIdempotency(authorized, idempotencyKey, current.accessKey().value(), documentId,
                    Instant.now(clock));
            if (!claimed) return documents.find(authorized, documentId).orElseThrow();
            return documents.save(authorized, documentId, current.accessKey(),
                    NfeRetrievalResult.success(new RawNfePayload(raw.json(), raw.provider(), raw.providerVersion()), canonical),
                    Instant.now(clock));
        });
    }
}
