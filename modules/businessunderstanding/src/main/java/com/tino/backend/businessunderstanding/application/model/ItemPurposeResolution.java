package com.tino.backend.businessunderstanding.application.model;

import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeAuthority;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionEvidence;
import java.math.BigDecimal;
import java.util.List;

public record ItemPurposeResolution(
        ItemPurpose purpose,
        BigDecimal confidence,
        String resolution,
        ItemPurposeAuthority authority,
        boolean needsConfirmation,
        List<ItemPurpose> suggestions,
        List<ItemPurposeResolutionEvidence> evidence) {
    public ItemPurposeResolution {
        suggestions = List.copyOf(suggestions);
        evidence = List.copyOf(evidence);
    }

    public ItemPurposeResolution(ItemPurpose purpose, BigDecimal confidence, String resolution,
            boolean needsConfirmation, List<ItemPurpose> suggestions) {
        this(purpose, confidence, resolution, authorityFor(resolution, purpose),
                needsConfirmation, suggestions, List.of());
    }

    private static ItemPurposeAuthority authorityFor(String resolution, ItemPurpose purpose) {
        try {
            return ItemPurposeAuthority.valueOf(resolution);
        } catch (RuntimeException exception) {
            return purpose == ItemPurpose.UNKNOWN ? ItemPurposeAuthority.UNKNOWN
                    : ItemPurposeAuthority.SYSTEM_SUGGESTED;
        }
    }
}
