package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.model.OtpIdentityProof;
import com.tino.backend.identity.application.usecase.ConsumeOtpVerificationTicket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Private bridge contract reserved for the Keycloak OTP authenticator integration. */
@RestController
@RequestMapping("/internal/v1/identity/otp")
public class OtpInternalTicketController {
    private final ConsumeOtpVerificationTicket consumeTicket;
    private final byte[] internalToken;

    public OtpInternalTicketController(
            ConsumeOtpVerificationTicket consumeTicket,
            @Value("${tino.identity.otp.internal-token:}") String internalToken) {
        this.consumeTicket = consumeTicket;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/tickets/consume")
    @Transactional
    public ResponseEntity<IdentityProofResponse> consume(
            @RequestHeader(value = "X-Tino-Internal-Token", required = false) String suppliedToken,
            @RequestBody(required = false) TicketRequest request) {
        if (!matches(suppliedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.ticket() == null || request.ticket().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toResponse(consumeTicket.execute(request.ticket(), request.clientId())));
    }

    private boolean matches(String suppliedToken) {
        return internalToken.length > 0
                && suppliedToken != null
                && MessageDigest.isEqual(
                        internalToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static IdentityProofResponse toResponse(OtpIdentityProof proof) {
        return new IdentityProofResponse(
                proof.challengeId(), proof.phone().e164(), proof.remainingSeconds());
    }

    public record IdentityProofResponse(
            UUID challengeId, String phoneE164, long remainingSeconds) {}

    public record TicketRequest(String ticket, String clientId) {}
}
