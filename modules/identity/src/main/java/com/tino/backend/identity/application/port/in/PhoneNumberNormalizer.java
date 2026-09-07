package com.tino.backend.identity.application.port.in;

/** Public identity boundary for canonicalizing a user-entered phone number. */
@FunctionalInterface
public interface PhoneNumberNormalizer {
    String normalize(String phoneInput);
}
