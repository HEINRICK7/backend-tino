package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.OtpVerificationEvent;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for trusted provider evidence and replay protection. */
public interface OtpVerificationEventRepository {
    Optional<OtpVerificationEvent> findByProviderEventId(String providerEventId);

    Optional<OtpVerificationEvent> findByChallengeId(UUID challengeId);

    void insert(OtpVerificationEvent event);
}
