package com.tino.backend.identity.adapter.out.crypto;

import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 verifier with a runtime-only pepper. */
public final class HmacOtpSecretHasher implements OtpSecretHasher {
    private final byte[] secret;

    public HmacOtpSecretHasher(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("OTP hash secret is required when OTP is enabled");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    @Override
    public String hash(String purpose, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) ':');
            mac.update(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("could not hash OTP secret", exception);
        }
    }

    public static boolean matches(String expected, String actual) {
        return expected != null
                && actual != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
