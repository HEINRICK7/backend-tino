package com.tino.backend.messaging.adapter.out.provider;

import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;

public final class SandboxWhatsAppDeliveryAdapter implements WhatsAppDeliveryPort {
    @Override
    public DeliveryResult send(WhatsAppDeliveryCommand command) {
        return new DeliveryResult("sandbox", "sandbox-whatsapp-" + command.messageId());
    }
}
