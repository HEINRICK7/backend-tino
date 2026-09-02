package com.tino.backend.fiscal.application.port.out;

import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NfeDocumentRepository {
    Optional<NfeDocumentSnapshot> findByAccessKey(BusinessId businessId, NfeAccessKey accessKey);
    Optional<NfeDocumentSnapshot> find(BusinessId businessId, UUID documentId);
    NfeDocumentSnapshot save(BusinessId businessId, UUID documentId, NfeAccessKey accessKey,
            NfeRetrievalResult result, Instant now);
    Optional<RetrievalIdempotency> findIdempotency(BusinessId businessId, String key);
    boolean claimIdempotency(BusinessId businessId, String key, String accessKey, UUID documentId, Instant now);

    record RetrievalIdempotency(String accessKey, UUID documentId) {}
}
