package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.PhoneNumber;
import java.util.UUID;

/** Delivery boundary; application code does not know the provider implementation. */
public interface OtpDeliveryPort {
    OtpDeliveryResult deliver(OtpDeliveryRequest request);

    record OtpDeliveryRequest(
            PhoneNumber recipient,
            String template,
            String code,
            int expiresMinutes,
            UUID correlationId) {
        public OtpDeliveryRequest {
            if (recipient == null || template == null || template.isBlank() || code == null
                    || !code.matches("[0-9]{6}") || expiresMinutes <= 0 || correlationId == null) {
                throw new IllegalArgumentException("OTP delivery request is incomplete");
            }
        }
    }

    record OtpDeliveryResult(Status status, Channel channel, String providerMessageId) {
        public OtpDeliveryResult {
            if (status == null || channel == null) {
                throw new IllegalArgumentException("delivery result is incomplete");
            }
            if (providerMessageId != null && providerMessageId.isBlank()) {
                throw new IllegalArgumentException("provider message id cannot be blank");
            }
        }
    }

    enum Status {
        ACCEPTED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    enum Channel {
        WHATSAPP,
        NONE
    }
}
