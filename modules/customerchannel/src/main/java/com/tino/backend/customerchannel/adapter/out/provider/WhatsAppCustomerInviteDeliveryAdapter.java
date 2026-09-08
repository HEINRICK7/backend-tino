package com.tino.backend.customerchannel.adapter.out.provider;

import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import java.util.UUID;

/** Bridges the customer-channel intent to the existing provider-neutral WhatsApp port. */
public final class WhatsAppCustomerInviteDeliveryAdapter implements CustomerInviteDeliveryPort {
    private final WhatsAppDeliveryPort delivery;

    public WhatsAppCustomerInviteDeliveryAdapter(WhatsAppDeliveryPort delivery) {
        this.delivery = delivery;
    }

    @Override
    public void send(String recipientPhone, String text, String idempotencyKey) {
        delivery.send(new WhatsAppDeliveryPort.WhatsAppDeliveryCommand(
                UUID.randomUUID(), idempotencyKey, recipientPhone, text,
                "text/plain", "customer-invite.txt", new byte[0]));
    }
}
