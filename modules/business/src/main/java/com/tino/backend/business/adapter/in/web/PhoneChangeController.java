package com.tino.backend.business.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.business.application.model.PhoneChangeResult;
import com.tino.backend.business.application.model.PhoneChangeChallenge;
import com.tino.backend.business.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.business.application.usecase.CompletePhoneChange;
import com.tino.backend.business.application.usecase.RequestPhoneChange;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** Authenticated recovery flow for replacing the phone bound to a business user. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/phone-change")
public class PhoneChangeController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final RequestPhoneChange requestPhoneChange;
    private final CompletePhoneChange completePhoneChange;

    public PhoneChangeController(
            AuthenticatedUserResolver authenticatedUsers,
            RequestPhoneChange requestPhoneChange,
            CompletePhoneChange completePhoneChange) {
        this.authenticatedUsers = authenticatedUsers;
        this.requestPhoneChange = requestPhoneChange;
        this.completePhoneChange = completePhoneChange;
    }

    @PostMapping("/challenges")
    @Transactional
    public ResponseEntity<ChallengeResponse> requestChallenge(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @Valid @RequestBody PhoneChangeRequest request,
            HttpServletRequest httpRequest) {
        var user = resolve(principal);
        var issued = requestPhoneChange.execute(
                user.userId().value(), new BusinessId(businessId), request.newPhone(),
                httpRequest == null ? null : httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(issued));
    }

    @PostMapping("/complete")
    @Transactional
    public PhoneChangeResponse complete(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @Valid @RequestBody CompleteRequest request) {
        var user = resolve(principal);
        var result = completePhoneChange.execute(
                user.userId().value(), new BusinessId(businessId), request.challengeId(), request.verificationTicket());
        return new PhoneChangeResponse(result.phone(), result.status());
    }

    private com.tino.backend.business.application.model.AuthenticatedUser resolve(
            AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new com.tino.backend.business.application.exception.InactiveAuthenticatedUserException();
        }
        try {
            var user = authenticatedUsers.resolve(principal.value());
            if (!user.active()) {
                throw new com.tino.backend.business.application.exception.InactiveAuthenticatedUserException();
            }
            return user;
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            throw new com.tino.backend.business.application.exception.InactiveAuthenticatedUserException();
        }
    }

    private static ChallengeResponse toResponse(PhoneChangeChallenge issued) {
        return new ChallengeResponse(
                issued.challengeId(), issued.expiresInSeconds(), issued.resendAvailableInSeconds(),
                issued.deliveryChannel());
    }

    public record PhoneChangeRequest(@JsonProperty("new_phone") @NotBlank String newPhone) {}

    public record CompleteRequest(
            @JsonProperty("challenge_id") @NotNull UUID challengeId,
            @JsonProperty("verification_ticket") @NotBlank String verificationTicket) {}

    public record ChallengeResponse(
            @JsonProperty("challenge_id") UUID challengeId,
            @JsonProperty("expires_in_seconds") long expiresInSeconds,
            @JsonProperty("resend_available_in_seconds") long resendAvailableInSeconds,
            @JsonProperty("delivery_channel") String deliveryChannel) {}

    public record PhoneChangeResponse(String phone, String status) {}
}
