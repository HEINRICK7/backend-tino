package com.tino.backend.messaging.application.model;

import java.time.Instant;
import java.util.UUID;

public record WhatsAppDeliveryView(
        UUID messageId,
        String status,
        String providerMessageId,
        int attempts,
        Instant sentAt,
        Instant failedAt) {}
