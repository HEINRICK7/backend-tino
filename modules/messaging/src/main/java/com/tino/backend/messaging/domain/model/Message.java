package com.tino.backend.messaging.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.UUID;

public record Message(UUID id, BusinessId businessId, UUID customerId, MessageChannel channel,
        MessagePurpose purpose, MessageTemplate template, String recipientRefHash, String idempotencyKey,
        String requestFingerprint, MessageStatus status, String provider, String providerMessageId,
        long version, Instant createdAt, Instant updatedAt) {}
