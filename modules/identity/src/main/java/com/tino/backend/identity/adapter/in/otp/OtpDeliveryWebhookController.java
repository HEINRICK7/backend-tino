package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.usecase.UpdateOtpDeliveryStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal provider receipt endpoint; delivery never authenticates a user. */
@RestController
@RequestMapping("/internal/v1/identity/otp/delivery-events")
public class OtpDeliveryWebhookController {
    private final UpdateOtpDeliveryStatus update;
    private final byte[] internalToken;

    public OtpDeliveryWebhookController(UpdateOtpDeliveryStatus update,
            @Value("${tino.identity.otp.internal-token:}") String internalToken) {
        this.update = update;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DeliveryResponse> receive(
            @RequestHeader(value = "X-Tino-Internal-Token", required = false) String suppliedToken,
            @RequestBody(required = false) DeliveryEvent event) {
        if (!matches(suppliedToken)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (event == null) return ResponseEntity.badRequest().build();
        var status = update.execute(event.providerEventId(), event.providerMessageId(), event.eventType(),
                event.recipientPhone(), event.occurredAt());
        return ResponseEntity.ok(new DeliveryResponse(event.providerMessageId(), status.canonical().name()));
    }

    private boolean matches(String suppliedToken) {
        return internalToken.length > 0 && suppliedToken != null
                && MessageDigest.isEqual(internalToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public record DeliveryEvent(String providerEventId, String providerMessageId,
            String eventType, String recipientPhone, Instant occurredAt) {}

    public record DeliveryResponse(String providerMessageId, String status) {}
}
