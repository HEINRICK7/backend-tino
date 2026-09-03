package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.OtpChallenge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for pre-authentication OTP challenges. */
public interface OtpChallengeRepository {
    void lockPhone(String phoneHash);

    Optional<OtpChallenge> findLatestPendingByPhoneHash(String phoneHash);

    long countCreatedSinceByPhoneHash(String phoneHash, Instant since);

    long countCreatedSinceByOriginHash(String originHash, Instant since);

    void insert(OtpChallenge challenge);

    Optional<OtpChallenge> findByIdForUpdate(UUID challengeId);

    Optional<OtpChallenge> findByTicketHashForUpdate(String ticketHash);

    Optional<OtpChallenge> findByProviderMessageIdForUpdate(String providerMessageId);

    void update(OtpChallenge challenge);

    int deleteFinishedBefore(Instant before);
}
