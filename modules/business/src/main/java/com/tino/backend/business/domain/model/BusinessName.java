package com.tino.backend.business.domain.model;

import java.util.Objects;

/** Operational business name; personal and legal identity data do not belong here. */
public record BusinessName(String value) {
    public static final int MAX_LENGTH = 200;

    public BusinessName {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("business trade name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("business trade name is too long");
        }
    }
}
