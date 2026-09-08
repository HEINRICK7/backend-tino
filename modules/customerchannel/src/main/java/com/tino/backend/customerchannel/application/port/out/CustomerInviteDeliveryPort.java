package com.tino.backend.customerchannel.application.port.out;

public interface CustomerInviteDeliveryPort {
    void send(String recipientPhone, String text, String idempotencyKey);
}
