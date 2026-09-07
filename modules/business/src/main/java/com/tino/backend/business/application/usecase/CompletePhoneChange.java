package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.model.PhoneChangeRequest;
import com.tino.backend.business.application.model.PhoneChangeResult;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.out.PhoneChangeRepository;
import com.tino.backend.identity.application.port.in.PhoneChangeOtpGateway;
import com.tino.backend.identity.application.port.in.PhoneIdentityManagement;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Completes a phone change only when the ticket belongs to the server-side request. */
public final class CompletePhoneChange {
    private final BusinessAuthorization authorization;
    private final PhoneChangeRepository requests;
    private final PhoneChangeOtpGateway otp;
    private final PhoneIdentityManagement identities;
    private final Clock clock;

    public CompletePhoneChange(
            BusinessAuthorization authorization,
            PhoneChangeRepository requests,
            PhoneChangeOtpGateway otp,
            PhoneIdentityManagement identities,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.otp = Objects.requireNonNull(otp, "otp");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PhoneChangeResult execute(
            UUID userId, BusinessId businessId, UUID challengeId, String verificationTicket) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(challengeId, "challengeId");
        if (verificationTicket == null || verificationTicket.isBlank()) {
            throw new IllegalArgumentException("verification ticket is required");
        }
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var request = requests.findByChallengeIdForUpdate(challengeId)
                    .orElseThrow(() -> new IllegalArgumentException("phone change request not found"));
            if (!request.userId().equals(userId)
                    || !request.businessId().value().equals(authorizedBusiness.value())) {
                throw new com.tino.backend.business.application.exception.BusinessAccessDeniedException();
            }
            if (request.status() == PhoneChangeRequest.Status.COMPLETED) {
                return new PhoneChangeResult(request.newPhoneE164(), "UPDATED");
            }
            if (request.status() != PhoneChangeRequest.Status.PENDING) {
                throw new IllegalArgumentException("phone change request is not pending");
            }
            final PhoneChangeResult[] result = new PhoneChangeResult[1];
            otp.consume(challengeId, verificationTicket, request.newPhoneE164(), () -> {
                identities.replacePhone(userId, request.newPhoneE164());
                requests.markCompleted(challengeId, Instant.now(clock));
                result[0] = new PhoneChangeResult(request.newPhoneE164(), "UPDATED");
            });
            return result[0];
        });
    }
}
