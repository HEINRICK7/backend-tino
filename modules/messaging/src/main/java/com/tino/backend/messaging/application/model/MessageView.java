package com.tino.backend.messaging.application.model;

import com.tino.backend.messaging.domain.model.Message;
import java.time.Instant;
import java.util.UUID;

public record MessageView(UUID id, UUID businessId, UUID customerId, String channel, String purpose,
        String template, String status, String provider, String providerMessageId, long version,
        Instant createdAt, Instant updatedAt) {
    public static MessageView from(Message m) { return new MessageView(m.id(), m.businessId().value(), m.customerId(),
            m.channel().name(), m.purpose().name(), m.template().name(), m.status().name(), m.provider(),
            m.providerMessageId(), m.version(), m.createdAt(), m.updatedAt()); }
}
