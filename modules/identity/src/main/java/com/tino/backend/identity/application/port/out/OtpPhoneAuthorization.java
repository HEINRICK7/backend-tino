package com.tino.backend.identity.application.port.out;

import java.util.UUID;

/** Server-side phone-to-user/business authorization used before OTP delivery. */
public interface OtpPhoneAuthorization {
    boolean isAuthorized(String phoneE164, String phoneHash, UUID businessId);

    void bind(String phoneHash, String externalSubject);
}
