package com.tino.backend.identity.adapter.out.crypto;

import com.tino.backend.identity.application.port.out.OtpGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/** Runtime-only generator for six-digit OTPs and opaque one-time tickets. */
public final class SecureOtpGenerator implements OtpGenerator {
    private final SecureRandom random;

    public SecureOtpGenerator(SecureRandom random) {
        this.random = random;
    }

    @Override
    public String code() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    @Override
    public String verificationTicket() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
