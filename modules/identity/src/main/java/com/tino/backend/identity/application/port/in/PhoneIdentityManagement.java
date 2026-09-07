package com.tino.backend.identity.application.port.in;

import java.util.UUID;

/**
 * Authenticated identity-management boundary used by the Business module when
 * a verified recovery flow replaces the phone bound to a user.
 */
@FunctionalInterface
public interface PhoneIdentityManagement {
    void replacePhone(UUID userId, String phoneE164);
}
