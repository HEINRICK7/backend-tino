package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpVerificationEventRepository;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
import com.tino.backend.identity.domain.model.OtpVerificationEvent;
import com.tino.backend.identity.domain.model.PhoneNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Applies only a trusted, normalized Go event; it never issues a session or token. */
public final class ConfirmOtpFromWhatsApp {
    private final OtpChallengeRepository challenges;
    private final OtpVerificationEventRepository events;
    private final Clock clock;

    public ConfirmOtpFromWhatsApp(OtpChallengeRepository challenges,
            OtpVerificationEventRepository events, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpChallengeStatus execute(UUID challengeId, String eventType, String providerEventId,
            String providerMessageId, String senderPhone, Instant occurredAt) {
        if (challengeId == null || !"AUTH_CONFIRMED".equals(eventType)
                || blank(providerEventId) || blank(providerMessageId) || blank(senderPhone)
                || occurredAt == null) {
            throw new IllegalArgumentException("invalid WhatsApp OTP event");
        }
        var challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("unknown OTP challenge"));
        var duplicate = events.findByProviderEventId(providerEventId);
        if (duplicate.isPresent()) {
            if (!duplicate.orElseThrow().challengeId().equals(challengeId)) {
                throw new IllegalArgumentException("provider event is bound to another challenge");
            }
            return challenge.status();
        }
        var now = Instant.now(clock);
        var sender = PhoneNumber.normalize(senderPhone);
        if (!challenge.phone().equals(sender)) {
            throw new IllegalArgumentException("WhatsApp sender does not match challenge");
        }
        if (challenge.status() == OtpChallengeStatus.VERIFIED) {
            return OtpChallengeStatus.VERIFIED;
        }
        if (challenge.status() != OtpChallengeStatus.PENDING) {
            throw new IllegalArgumentException("OTP challenge is not pending");
        }
        if (challenge.isExpired(now)) {
            challenges.update(challenge.expired());
            throw new IllegalArgumentException("OTP challenge expired");
        }
        events.insert(new OtpVerificationEvent(providerEventId, challengeId, providerMessageId,
                sender, occurredAt, now));
        challenges.update(challenge.whatsappVerified(now));
        return OtpChallengeStatus.VERIFIED;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
