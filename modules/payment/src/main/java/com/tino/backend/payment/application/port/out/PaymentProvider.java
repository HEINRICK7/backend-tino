package com.tino.backend.payment.application.port.out;

import com.tino.backend.payment.domain.model.Payment;
import java.util.UUID;

public interface PaymentProvider {
    String name();
    Authorization authorize(Payment payment);
    boolean verifySignature(String payload, String signature);

    record Authorization(String providerPaymentId, String providerEventId, UUID eventId) {}
}
