package com.tino.backend.businessunderstanding.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Context in which the business uses an item. It is intentionally open-ended:
 * the domain stores the business vocabulary without turning one context into
 * a universal product rule.
 */
public record UsageContext(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public static final UsageContext LEGACY = new UsageContext("LEGACY");

    public UsageContext {
        Objects.requireNonNull(value, "value");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("usage context must use an uppercase code");
        }
    }

    public static UsageContext of(String value) {
        return new UsageContext(value);
    }

    public static UsageContext orLegacy(String value) {
        return value == null || value.isBlank() ? LEGACY : of(value);
    }
}
