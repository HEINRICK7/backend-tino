package com.tino.backend.identity.application.model;

import com.tino.backend.identity.application.port.out.OtpDeliveryPort;
import com.tino.backend.identity.domain.model.OtpLifecycleStatus;
import java.util.Objects;
import java.util.UUID;

/** Safe response for a successfully accepted OTP delivery. */
public record OtpChallengeIssued(
        UUID challengeId,
        long expiresInSeconds,
        long resendAvailableInSeconds,
        OtpDeliveryPort.Channel deliveryChannel,
        OtpLifecycleStatus status) {
    public OtpChallengeIssued {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(deliveryChannel, "deliveryChannel");
        Objects.requireNonNull(status, "status");
    }
}
