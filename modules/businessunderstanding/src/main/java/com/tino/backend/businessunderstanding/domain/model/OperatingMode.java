package com.tino.backend.businessunderstanding.domain.model;

import java.util.Locale;

import java.util.Arrays;

public enum OperatingMode {
    RESELLS_GOODS,
    PRODUCES_GOODS,
    PROVIDES_SERVICES,
    BUYS_INPUTS;

    public static OperatingMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("operating mode is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported operating mode", exception);
        }
    }

    public static boolean isKnown(String value) {
        return value != null && Arrays.stream(values()).anyMatch(item -> item.name().equals(value));
    }
}
