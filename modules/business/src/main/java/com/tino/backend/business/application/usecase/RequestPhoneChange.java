package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.model.PhoneChangeRequest;
import com.tino.backend.business.application.model.PhoneChangeChallenge;
import com.tino.backend.business.application.port.out.PhoneChangeRepository;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.identity.application.port.in.PhoneChangeOtpGateway;
import com.tino.backend.identity.application.port.in.PhoneNumberNormalizer;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

/** Starts a phone change only for an active member of the requested business. */
public final class RequestPhoneChange {
    private final BusinessAuthorization authorization;
    private final PhoneChangeOtpGateway otp;
    private final PhoneChangeRepository requests;
    private final PhoneNumberNormalizer phoneNumbers;

    public RequestPhoneChange(
            BusinessAuthorization authorization,
            PhoneChangeOtpGateway otp,
            PhoneChangeRepository requests,
            PhoneNumberNormalizer phoneNumbers) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.otp = Objects.requireNonNull(otp, "otp");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.phoneNumbers = Objects.requireNonNull(phoneNumbers, "phoneNumbers");
    }

    public PhoneChangeChallenge execute(
            UUID userId, BusinessId businessId, String newPhone, String requestOrigin) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(businessId, "businessId");
        var normalized = phoneNumbers.normalize(newPhone);
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var issued = otp.request(normalized, requestOrigin, authorizedBusiness.value());
            var existing = requests.findPendingByUserBusinessAndPhone(
                    userId, authorizedBusiness.value(), normalized);
            if (existing.isEmpty() || !existing.orElseThrow().challengeId().equals(issued.challengeId())) {
                requests.insert(new PhoneChangeRequest(
                        issued.challengeId(), userId, authorizedBusiness, normalized,
                        PhoneChangeRequest.Status.PENDING, java.time.Instant.now(), null));
            }
            return new PhoneChangeChallenge(
                    issued.challengeId(), issued.expiresInSeconds(),
                    issued.resendAvailableInSeconds(), issued.deliveryChannel());
        });
    }
}
