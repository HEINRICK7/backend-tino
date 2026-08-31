package com.tino.backend.identity.domain.model;

import java.util.Objects;

/** A normalized Brazilian phone number in E.164 form. */
public record PhoneNumber(String e164) {
    public PhoneNumber {
        Objects.requireNonNull(e164, "e164");
        if (!e164.matches("\\+55(?:[1-9][0-9])(?:9[0-9]{8}|[2-5][0-9]{7})")) {
            throw new IllegalArgumentException("invalid Brazilian phone number");
        }
    }

    public static PhoneNumber normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        var digits = input.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && (digits.length() == 11 || digits.length() == 12)) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("55")) {
            return new PhoneNumber("+" + digits);
        }
        if (digits.length() == 10 || digits.length() == 11) {
            return new PhoneNumber("+55" + digits);
        }
        throw new IllegalArgumentException("invalid Brazilian phone number");
    }
}
