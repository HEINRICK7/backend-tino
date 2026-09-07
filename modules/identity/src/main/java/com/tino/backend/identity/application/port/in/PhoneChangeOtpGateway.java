package com.tino.backend.identity.application.port.in;

import java.util.UUID;

/** Identity-facing OTP contract for the authenticated Business phone-change flow. */
public interface PhoneChangeOtpGateway {
    PhoneChangeChallenge request(String phoneE164, String requestOrigin, UUID businessId);

    void consume(
            UUID challengeId,
            String verificationTicket,
            String expectedPhoneE164,
            Runnable beforeConsume);

    record PhoneChangeChallenge(
            UUID challengeId,
            long expiresInSeconds,
            long resendAvailableInSeconds,
            String deliveryChannel) {}
}
