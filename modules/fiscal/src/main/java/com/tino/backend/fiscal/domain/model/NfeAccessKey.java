package com.tino.backend.fiscal.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated NF-e access key. Numeric keys also receive the modulo-11 check. */
public record NfeAccessKey(String value) {
    private static final Pattern ALPHANUMERIC_KEY = Pattern.compile("[0-9A-Z]{44}");

    public NfeAccessKey {
        value = normalize(value);
        if (!ALPHANUMERIC_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("NF-e access key must contain 44 alphanumeric characters");
        }
        if (value.chars().allMatch(Character::isDigit) && !hasValidCheckDigit(value)) {
            throw new IllegalArgumentException("NF-e access key has an invalid check digit");
        }
    }

    private static String normalize(String input) {
        Objects.requireNonNull(input, "NF-e access key");
        return input.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private static boolean hasValidCheckDigit(String key) {
        var sum = 0;
        var weight = 2;
        for (var index = key.length() - 2; index >= 0; index--, weight++) {
            if (weight == 10) weight = 2;
            sum += Character.digit(key.charAt(index), 10) * weight;
        }
        var remainder = sum % 11;
        var check = remainder == 0 || remainder == 1 ? 0 : 11 - remainder;
        return check == Character.digit(key.charAt(key.length() - 1), 10);
    }
}
