package com.tino.backend.business.application.port.out;

import com.tino.backend.business.application.model.PhoneChangeRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PhoneChangeRepository {
    Optional<PhoneChangeRequest> findByChallengeIdForUpdate(UUID challengeId);

    Optional<PhoneChangeRequest> findPendingByUserBusinessAndPhone(
            UUID userId, UUID businessId, String phoneE164);

    void insert(PhoneChangeRequest request);

    void markCompleted(UUID challengeId, Instant completedAt);
}
