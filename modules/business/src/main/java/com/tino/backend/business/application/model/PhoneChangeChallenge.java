package com.tino.backend.business.application.model;

import java.util.UUID;

/** Business-facing representation of a phone-change verification challenge. */
public record PhoneChangeChallenge(
        UUID challengeId,
        long expiresInSeconds,
        long resendAvailableInSeconds,
        String deliveryChannel) {}
