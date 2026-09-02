package com.tino.backend.businessunderstanding.domain.model;

/**
 * Precedence of a classification. Higher values have more authority.
 */
public enum ItemPurposeAuthority {
    UNKNOWN(0),
    SYSTEM_SUGGESTED(1),
    LEARNED(2),
    USER_CONFIRMED(3);

    private final int rank;

    ItemPurposeAuthority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
