package com.tino.backend.businessunderstanding.domain.model;

import java.util.Locale;

import java.util.Arrays;

public enum ActivityCode {
    MERCADINHO("Mercadinho"),
    ACOUGUE("Açougue"),
    VERDUREIRA("Verdureira"),
    PADARIA("Padaria"),
    CONFEITARIA("Confeitaria"),
    RESTAURANTE("Restaurante"),
    LANCHONETE("Lanchonete"),
    SALAO_BELEZA("Salão de beleza"),
    OFICINA("Oficina"),
    ENCOMENDAS("Encomendas"),
    OTHER("Outro");

    private final String label;

    ActivityCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ActivityCode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("activity code is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported activity code", exception);
        }
    }

    public static boolean isKnown(String value) {
        return value != null && Arrays.stream(values()).anyMatch(item -> item.name().equals(value));
    }
}
