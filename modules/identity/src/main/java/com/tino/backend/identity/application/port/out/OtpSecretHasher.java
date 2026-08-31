package com.tino.backend.identity.application.port.out;

/** Hashes OTP-related secrets without exposing the configured pepper. */
public interface OtpSecretHasher {
    String hash(String purpose, String value);

    default String hashPhone(String phone) {
        return hash("phone", phone);
    }

    default String hashOrigin(String origin) {
        return hash("origin", origin);
    }

    default String hashCode(String challengeId, String phone, String code) {
        return hash("code", challengeId + ":" + phone + ":" + code);
    }

    default String hashTicket(String ticket) {
        return hash("ticket", ticket);
    }
}
