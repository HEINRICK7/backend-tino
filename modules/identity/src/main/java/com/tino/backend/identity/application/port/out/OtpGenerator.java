package com.tino.backend.identity.application.port.out;

/** Cryptographically secure values needed by the OTP use cases. */
public interface OtpGenerator {
    String code();

    String verificationTicket();
}
