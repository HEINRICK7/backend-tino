package com.tino.backend.payment.adapter.out.provider;

import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.domain.model.Payment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deterministic local provider; it never performs network I/O. */
@Component
public final class SandboxPaymentProvider implements PaymentProvider {
    private final String webhookSecret;

    public SandboxPaymentProvider(
            @Value("${tino.payment.sandbox-webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    @Override
    public String name() { return "sandbox"; }

    @Override
    public Authorization authorize(Payment payment) {
        var suffix = payment.id().toString();
        return new Authorization("sandbox-payment-" + suffix, "sandbox-authorize-" + suffix,
                payment.id());
    }

    @Override
    public boolean verifySignature(String payload, String signature) {
        if (webhookSecret.isBlank() || payload == null || signature == null) return false;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            return false;
        }
    }
}
