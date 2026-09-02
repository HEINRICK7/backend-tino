package com.tino.backend.businessunderstanding.application.model;

import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import java.math.BigDecimal;
import java.util.List;

public record ItemPurposeResolution(
        ItemPurpose purpose,
        BigDecimal confidence,
        String resolution,
        boolean needsConfirmation,
        List<ItemPurpose> suggestions) {
    public ItemPurposeResolution {
        suggestions = List.copyOf(suggestions);
    }
}
