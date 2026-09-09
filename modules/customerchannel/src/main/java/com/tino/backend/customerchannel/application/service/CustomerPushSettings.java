package com.tino.backend.customerchannel.application.service;

import java.time.Duration;

/** Runtime-only Web Push configuration. The private VAPID key never leaves the backend. */
public record CustomerPushSettings(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject,
        Duration dispatchInterval,
        Duration dispatchInitialDelay,
        Duration dispatchTimeout) {

    public CustomerPushSettings {
        vapidPublicKey = blankToNull(vapidPublicKey);
        vapidPrivateKey = blankToNull(vapidPrivateKey);
        vapidSubject = blankToNull(vapidSubject);
        if (dispatchInterval == null || dispatchInterval.isZero() || dispatchInterval.isNegative()) {
            throw new IllegalArgumentException("push dispatch interval must be positive");
        }
        if (dispatchInitialDelay == null || dispatchInitialDelay.isNegative()) {
            throw new IllegalArgumentException("push dispatch initial delay must not be negative");
        }
        if (dispatchTimeout == null || dispatchTimeout.isZero() || dispatchTimeout.isNegative()) {
            throw new IllegalArgumentException("push dispatch timeout must be positive");
        }
        enabled = enabled && vapidPublicKey != null && vapidPrivateKey != null && vapidSubject != null;
    }

    public static CustomerPushSettings disabled() {
        return new CustomerPushSettings(false, null, null, null,
                Duration.ofSeconds(5), Duration.ZERO, Duration.ofSeconds(5));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
