package com.tino.backend.businessunderstanding.domain.model;

public enum ItemPurposeSource {
    USER_CONFIRMED,
    LEARNED,
    SYSTEM_SUGGESTED,
    MIGRATED;

    public ItemPurposeAuthority authority() {
        return switch (this) {
            case USER_CONFIRMED -> ItemPurposeAuthority.USER_CONFIRMED;
            case LEARNED -> ItemPurposeAuthority.LEARNED;
            case SYSTEM_SUGGESTED -> ItemPurposeAuthority.SYSTEM_SUGGESTED;
            case MIGRATED -> ItemPurposeAuthority.UNKNOWN;
        };
    }
}
