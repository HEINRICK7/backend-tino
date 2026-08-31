package com.tino.backend.identity.adapter.out.delivery;

import com.tino.backend.identity.application.port.out.OtpDeliveryPort;

/** Explicitly disabled adapter; it never pretends that an OTP was delivered. */
public final class DisabledOtpDeliveryAdapter implements OtpDeliveryPort {
    @Override
    public OtpDeliveryResult deliver(OtpDeliveryRequest request) {
        return new OtpDeliveryResult(Status.PERMANENT_FAILURE, Channel.NONE);
    }
}
