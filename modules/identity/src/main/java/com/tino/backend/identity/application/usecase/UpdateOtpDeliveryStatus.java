package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpDeliveryEventRepository;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
import com.tino.backend.identity.domain.model.OtpDeliveryEvent;
import com.tino.backend.identity.domain.model.PhoneNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Applies a trusted Evolution delivery receipt without granting authentication. */
public final class UpdateOtpDeliveryStatus {
    private final OtpChallengeRepository challenges;
    private final OtpDeliveryEventRepository events;
    private final Clock clock;

    public UpdateOtpDeliveryStatus(OtpChallengeRepository challenges,
            OtpDeliveryEventRepository events, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpChallengeStatus execute(String providerEventId, String providerMessageId,
            String eventType, String recipientPhone, Instant occurredAt) {
        if (blank(providerEventId) || blank(providerMessageId) || blank(eventType)
                || occurredAt == null) {
            throw new IllegalArgumentException("invalid OTP delivery event");
        }
        var duplicate = events.findByProviderEventId(providerEventId);
        if (duplicate.isPresent()) {
            return challenges.findByIdForUpdate(duplicate.orElseThrow().challengeId())
                    .orElseThrow(() -> new IllegalArgumentException("unknown OTP challenge"))
                    .status();
        }
        var challenge = challenges.findByProviderMessageIdForUpdate(providerMessageId)
                .orElseThrow(() -> new IllegalArgumentException("unknown OTP provider message"));
        // Evolution can report the recipient as a WhatsApp LID (for example
        // 213631807533308@lid) instead of the phone number. The provider
        // message id already binds this receipt to the OTP challenge, so use
        // the challenge phone when the provider cannot expose a PN number.
        var recipient = blank(recipientPhone) ? challenge.phone() : PhoneNumber.normalize(recipientPhone);
        if (!blank(recipientPhone) && !sameBrazilianPhoneVariant(challenge.phone(), recipient)) {
            throw new IllegalArgumentException("OTP delivery recipient does not match challenge");
        }
        var now = Instant.now(clock);
        if (challenge.isExpired(now)) {
            if (challenge.status() == OtpChallengeStatus.PENDING
                    || challenge.status() == OtpChallengeStatus.DELIVERED) {
                challenges.update(challenge.expired());
            }
            throw new IllegalArgumentException("OTP challenge expired");
        }
        var event = new OtpDeliveryEvent(providerEventId, challenge.id(), providerMessageId,
                recipient, eventType, occurredAt, now);
        events.insert(event);
        if ("AUTH_DELIVERED".equals(eventType) && challenge.status() == OtpChallengeStatus.PENDING) {
            challenge = challenge.delivered();
        } else if ("AUTH_DELIVERY_FAILED".equals(eventType)
                && (challenge.status() == OtpChallengeStatus.PENDING
                        || challenge.status() == OtpChallengeStatus.DELIVERED)) {
            challenge = challenge.deliveryFailed();
        }
        challenges.update(challenge);
        return challenge.status();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean sameBrazilianPhoneVariant(PhoneNumber expected, PhoneNumber actual) {
        return expected.equals(actual)
                || withoutBrazilianNinthDigit(expected.e164()).equals(actual.e164())
                || withoutBrazilianNinthDigit(actual.e164()).equals(expected.e164());
    }

    private static String withoutBrazilianNinthDigit(String phone) {
        if (phone.matches("\\+55[1-9][0-9]9[0-9]{8}")) {
            return phone.substring(0, 5) + phone.substring(6);
        }
        return phone;
    }
}
