package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.usecase.ConfirmOtpFromWhatsApp;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal Go-to-Java boundary; it never creates a session or emits tokens. */
@RestController
@RequestMapping("/internal/v1/identity/otp/events")
public class OtpWebhookController {
    private final ConfirmOtpFromWhatsApp confirm;
    private final byte[] internalToken;

    public OtpWebhookController(
            ConfirmOtpFromWhatsApp confirm,
            @Value("${tino.identity.otp.internal-token:}") String internalToken) {
        this.confirm = confirm;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<EventResponse> receive(
            @RequestHeader(value = "X-Tino-Internal-Token", required = false) String suppliedToken,
            @RequestBody(required = false) ConfirmationEvent event) {
        if (!matches(suppliedToken)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (event == null) return ResponseEntity.badRequest().build();
        var status = confirm.execute(event.correlationId(), event.eventType(), event.providerEventId(),
                event.providerMessageId(), event.senderPhone(), event.occurredAt());
        return ResponseEntity.ok(new EventResponse(event.correlationId(), status.name()));
    }

    private boolean matches(String suppliedToken) {
        return internalToken.length > 0 && suppliedToken != null
                && MessageDigest.isEqual(internalToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public record ConfirmationEvent(
            UUID correlationId,
            String eventType,
            String providerEventId,
            String providerMessageId,
            String senderPhone,
            Instant occurredAt) {}

    public record EventResponse(UUID challengeId, String status) {}
}
