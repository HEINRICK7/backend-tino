package com.tino.backend.messaging.application.port.out;

import java.util.UUID;

public interface WhatsAppDeliveryPort {
    DeliveryResult send(WhatsAppDeliveryCommand command);

    record WhatsAppDeliveryCommand(
            UUID messageId,
            String idempotencyKey,
            String recipientPhone,
            String text,
            String mimeType,
            String filename,
            byte[] media) {}

    record DeliveryResult(String provider, String providerMessageId) {}
}
