package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.PhoneNumber;

/** Delivery boundary; application code does not know the provider implementation. */
public interface OtpDeliveryPort {
    OtpDeliveryResult deliver(OtpDeliveryRequest request);

    record OtpDeliveryRequest(PhoneNumber destination, String code) {}

    record OtpDeliveryResult(Status status, Channel channel) {
        public OtpDeliveryResult {
            if (status == null || channel == null) {
                throw new IllegalArgumentException("delivery result is incomplete");
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
