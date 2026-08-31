package com.tino.backend.identity.application.model;

import com.tino.backend.identity.domain.model.PhoneNumber;
import java.util.Objects;
import java.util.UUID;

/** Result consumed only by the identity-provider bridge, never by tenant APIs. */
public record OtpIdentityProof(UUID challengeId, PhoneNumber phone, long remainingSeconds) {
    public OtpIdentityProof {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(phone, "phone");
    }
}
