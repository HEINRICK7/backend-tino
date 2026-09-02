package com.tino.backend.receiving.application.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Client-safe result item; it contains no persistence entity. */
public record GoodsReceiptItemResult(
        int lineNumber,
        UUID productId,
        String productName,
        String baseUnit,
        BigDecimal quantityAdded,
        BigDecimal unitCost) {}
